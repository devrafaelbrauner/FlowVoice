package dev.rafaelbrauner.flowvoice.localasr

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.rafaelbrauner.flowvoice.shared.localasr.NemotronModel
import dev.rafaelbrauner.flowvoice.shared.localasr.NemotronModelDir
import dev.rafaelbrauner.flowvoice.shared.localasr.NemotronRecognizer
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LocalModelState {
    data object NotInstalled : LocalModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : LocalModelState
    data object Verifying : LocalModelState
    data class Extracting(val progress: Float) : LocalModelState
    data object Installed : LocalModelState
    data class Failed(val message: String) : LocalModelState
}

// Baixa, confere e extrai o modelo do motor no aparelho. Uma tarefa por vez, na ordem em que foram
// pedidas: cada uma espera a anterior terminar de limpar o disco antes de mexer nele.
class LocalModelInstaller(context: Context, private val preferences: PreferencesStore) {
    private val filesDir: File = context.filesDir
    val modelDir: File = File(filesDir, NemotronModel.DIR_NAME)
    private val archivePart = File(filesDir, NemotronModel.DIR_NAME + ARCHIVE_PART_SUFFIX)
    private val scratch = listOf(
        archivePart,
        File(filesDir, NemotronModel.DIR_NAME + EXTRACTING_SUFFIX),
        File(filesDir, NemotronModel.DIR_NAME + REPLACED_SUFFIX)
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var job: Job? = null
    private var installing = false

    private val mutableState = MutableStateFlow(
        if (NemotronModelDir.isComplete(modelDir)) LocalModelState.Installed else LocalModelState.NotInstalled
    )
    val state: StateFlow<LocalModelState> = mutableState.asStateFlow()

    init {
        // Sobras de uma instalação que o sistema matou no meio.
        enqueue { deleteScratch() }
    }

    fun install(useWhenReady: Boolean) = synchronized(lock) {
        if (installing || mutableState.value == LocalModelState.Installed) return@synchronized
        installing = true
        mutableState.value = LocalModelState.Downloading(0, NemotronModel.ARCHIVE_BYTES)
        enqueue { runInstall(useWhenReady) }
    }

    fun cancel() = synchronized(lock) {
        if (!installing) return@synchronized
        installing = false
        job?.cancel()
        mutableState.value = LocalModelState.NotInstalled
        // A tarefa cancelada limpa o disco no finally; a próxima espera por ela.
    }

    fun delete() = synchronized(lock) {
        if (installing) {
            installing = false
            job?.cancel()
        }
        val previous = preferences.read()
        if (previous.transcriptionEngine == TranscriptionEngine.Local) {
            preferences.write(previous.copy(transcriptionEngine = TranscriptionEngine.Cloud))
        }
        mutableState.value = LocalModelState.NotInstalled
        enqueue {
            withContext(NonCancellable) {
                NemotronRecognizer.releaseNowIfIdle()
                modelDir.deleteRecursively()
                deleteScratch()
                Log.i(TAG, "model_deleted")
            }
        }
    }

    fun refresh() = synchronized(lock) {
        if (installing) return@synchronized
        val current = mutableState.value
        mutableState.value = when {
            NemotronModelDir.isComplete(modelDir) -> LocalModelState.Installed
            current is LocalModelState.Failed -> current
            else -> LocalModelState.NotInstalled
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        val previous = job
        job = scope.launch {
            previous?.join()
            block()
        }
    }

    private suspend fun runInstall(useWhenReady: Boolean) {
        val self = coroutineContext[Job]
        val started = SystemClock.elapsedRealtime()
        try {
            coroutineContext.ensureActive()
            val reclaimable = archivePart.length()
            if (!ModelDiskSpace.enough(filesDir.usableSpace, reclaimable, NemotronModel.REQUIRED_FREE_BYTES)) {
                Log.i(TAG, "model_install_no_space usable=${filesDir.usableSpace} required=${NemotronModel.REQUIRED_FREE_BYTES}")
                throw ModelInstallException(
                    "Espaço insuficiente: são precisos " +
                        "${ModelDiskSpace.gigabytes(NemotronModel.REQUIRED_FREE_BYTES)} livres"
                )
            }
            deleteScratch()
            val sha = download(self)
            val shaOk = sha == NemotronModel.ARCHIVE_SHA256
            Log.i(TAG, "model_download_done bytes=${archivePart.length()} ms=${elapsed(started)} sha=${if (shaOk) "ok" else "mismatch"}")
            publish(self, LocalModelState.Verifying)
            if (!shaOk) throw ModelInstallException(INVALID_ARCHIVE)
            publish(self, LocalModelState.Extracting(0f))
            var lastPercent = -1
            ModelArchive.extract(archivePart, modelDir, NemotronModel.FILES) { progress ->
                val percent = (progress * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    publish(self, LocalModelState.Extracting(progress))
                }
            }
            archivePart.delete()
            Log.i(TAG, "model_installed bytes=${NemotronModel.INSTALLED_BYTES} ms=${elapsed(started)}")
            synchronized(lock) {
                if (job !== self || !installing) return
                installing = false
                if (useWhenReady) {
                    preferences.write(preferences.read().copy(transcriptionEngine = TranscriptionEngine.Local))
                }
                mutableState.value = LocalModelState.Installed
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) { deleteScratch() }
            Log.i(TAG, "model_install_cancelled ms=${elapsed(started)}")
            throw error
        } catch (error: ModelInstallException) {
            fail(self, error.message ?: INVALID_ARCHIVE)
        } catch (error: Exception) {
            Log.i(TAG, "model_install_failed error=${error::class.simpleName}")
            fail(self, WRITE_FAILED)
        }
    }

    private suspend fun download(self: Job?): String {
        val connection = try {
            (URL(NemotronModel.ARCHIVE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
        } catch (_: IOException) {
            throw ModelInstallException(NETWORK_FAILED)
        }
        try {
            val code = try {
                connection.responseCode
            } catch (_: IOException) {
                throw ModelInstallException(NETWORK_FAILED)
            }
            Log.i(TAG, "model_download_start http=$code bytes=${connection.contentLengthLong}")
            if (code != HttpURLConnection.HTTP_OK) {
                throw ModelInstallException("O GitHub respondeu $code ao baixar o modelo — tente de novo")
            }
            val declared = connection.contentLengthLong
            if (declared >= 0 && declared != NemotronModel.ARCHIVE_BYTES) throw ModelInstallException(INVALID_ARCHIVE)
            var lastPublished = 0L
            return try {
                connection.inputStream.use { input ->
                    ModelArchive.receive(input, archivePart, NemotronModel.ARCHIVE_BYTES) { received ->
                        if (received - lastPublished >= PROGRESS_STEP_BYTES || received == NemotronModel.ARCHIVE_BYTES) {
                            lastPublished = received
                            publish(self, LocalModelState.Downloading(received, NemotronModel.ARCHIVE_BYTES))
                        }
                    }
                }
            } catch (error: IOException) {
                // Rede caiu no meio: o que já veio não serve (não há retomada).
                Log.i(TAG, "model_download_interrupted bytes=${archivePart.length()} error=${error::class.simpleName}")
                throw ModelInstallException(NETWORK_FAILED)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun publish(owner: Job?, value: LocalModelState) = synchronized(lock) {
        if (job === owner && installing) mutableState.value = value
    }

    private fun fail(owner: Job?, message: String) {
        deleteScratch()
        synchronized(lock) {
            if (job !== owner || !installing) return
            installing = false
            mutableState.value = LocalModelState.Failed(message)
        }
    }

    private fun deleteScratch() {
        scratch.forEach { it.deleteRecursively() }
    }

    private fun elapsed(started: Long) = SystemClock.elapsedRealtime() - started

    private companion object {
        const val TAG = "FlowVoiceModel"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val PROGRESS_STEP_BYTES = 1L shl 20
        const val NETWORK_FAILED = "Não foi possível baixar o modelo (sem conexão?) — tente de novo"
    }
}
