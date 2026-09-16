package dev.rafaelbrauner.flowvoice.shared.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat as PlatformAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AndroidAudioCaptureEngine(
    context: Context,
    override val format: AudioFormat = AudioFormat.DEFAULT,
    private val newReadPolicy: () -> CaptureReadPolicy = { CaptureReadPolicy() }
) : AudioCaptureEngine {
    private val appContext = context.applicationContext
    private val captureActive = AtomicBoolean(false)

    // P154: cada captura recebe um número. A thread antiga só continua lendo enquanto o número dela
    // ainda for o da captura corrente — assim uma thread que sobreviveu ao `stop()` (join estourou)
    // nunca volta a entregar frames por cima da sessão seguinte.
    private val runCounter = AtomicLong(0L)
    private val activeRun = AtomicLong(NO_RUN)

    @Volatile
    private var recordingHandle: RecordingHandle? = null
    private var captureThread: Thread? = null

    init {
        require(format.sampleBits == 16) { "sampleBits must be 16" }
    }

    override val isRunning: Boolean
        get() = captureActive.get() && (recordingHandle?.isRecording() ?: false)

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) = start(onFrame, onError = {})

    override suspend fun start(
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) {
        if (!captureActive.compareAndSet(false, true)) {
            throw AudioCaptureException("audio capture is already running")
        }
        try {
            startCapture(onFrame, onError)
        } catch (error: CancellationException) {
            stop()
            throw error
        } catch (error: AudioCaptureException) {
            stop()
            throw error
        } catch (error: Throwable) {
            stop()
            throw AudioCaptureException("failed to start audio capture", error)
        }
    }

    private suspend fun startCapture(
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) {
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw AudioCaptureException("RECORD_AUDIO permission is not granted")
        }
        val channelMask = resolveChannelMask(format.channels)
        val minBufferSize = AudioRecord.getMinBufferSize(
            format.sampleRate,
            channelMask,
            PlatformAudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            throw AudioCaptureException("unsupported audio capture format: $format")
        }

        // Frames de 100 ms (P140), com o buffer do AudioRecord maior que a leitura.
        val readBuffer = ByteArray(CaptureFrames.readBytes(format))
        val recordBufferBytes = CaptureFrames.recordBufferBytes(format, minBufferSize)
        val audioRecord = openRecord(channelMask, recordBufferBytes)

        val run = runCounter.incrementAndGet()
        activeRun.set(run)
        val handle = RecordingHandle(audioRecord)
        recordingHandle = handle
        val startSignal = CompletableDeferred<Unit>()
        val thread = Thread(
            {
                captureLoop(
                    run = run,
                    initialHandle = handle,
                    readBuffer = readBuffer,
                    channelMask = channelMask,
                    recordBufferBytes = recordBufferBytes,
                    startSignal = startSignal,
                    onFrame = onFrame,
                    onError = onError
                )
            },
            THREAD_NAME
        )
        captureThread = thread
        thread.start()

        try {
            withTimeout(START_TIMEOUT_MS) { startSignal.await() }
        } catch (error: TimeoutCancellationException) {
            throw AudioCaptureException("timed out waiting for audio capture to start", error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw if (error is AudioCaptureException) error else AudioCaptureException("failed to start audio capture", error)
        }
    }

    private fun captureLoop(
        run: Long,
        initialHandle: RecordingHandle,
        readBuffer: ByteArray,
        channelMask: Int,
        recordBufferBytes: Int,
        startSignal: CompletableDeferred<Unit>,
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) {
        var handle = initialHandle
        var failure: AudioCaptureException? = null
        val policy = newReadPolicy()
        try {
            handle.startRecording()
            startSignal.complete(Unit)
            Log.i(
                TAG,
                "audio capture started: sampleRate=${format.sampleRate}, channels=${format.channels}, sampleBits=${format.sampleBits}, readBufferBytes=${readBuffer.size}, recordBufferBytes=$recordBufferBytes"
            )
            while (isCurrentRun(run) && !Thread.currentThread().isInterrupted) {
                val bytesRead = handle.read(readBuffer)
                // O `stop()` destrava a leitura pendente, que volta com zero: reler a flag DEPOIS da
                // leitura é o que separa encerramento normal de microfone mudo.
                val stillActive = isCurrentRun(run)
                when (val action = policy.onRead(bytesRead, stillActive, SystemClock.elapsedRealtime())) {
                    is CaptureReadAction.Deliver ->
                        runBlocking { onFrame(AudioFrame(readBuffer.copyOf(bytesRead), format)) }

                    is CaptureReadAction.Stop ->
                        Log.i(TAG, "audio capture read returned while stopping: ${diagnose(bytesRead, handle, policy, stillActive)}")

                    is CaptureReadAction.WaitAndRetry -> {
                        // Só a primeira vazia da sequência vira linha de log: a pausa é de 20 ms e o
                        // log não pode virar enxurrada.
                        if (policy.consecutiveEmptyReads == 1) {
                            Log.w(TAG, "audio capture read empty, still capturing: ${diagnose(bytesRead, handle, policy, stillActive)}")
                        }
                        if (action.pauseMs > 0L) Thread.sleep(action.pauseMs)
                    }

                    is CaptureReadAction.Restart -> {
                        Log.w(TAG, "audio capture restarting: ${diagnose(bytesRead, handle, policy, stillActive)}")
                        // Nada de áudio se perde aqui: o que já foi lido saiu em `onFrame` e o buffer
                        // do sistema está vazio, que é justamente o motivo da reabertura.
                        handle.release()
                        val reopened = runCatching { openRecord(channelMask, recordBufferBytes) }
                            .map { RecordingHandle(it) }
                            .onFailure { Log.e(TAG, "audio capture reopen failed", it) }
                            .getOrNull()
                        if (reopened == null) {
                            failure = giveUp(run, "failed to reopen AudioRecord")
                        } else {
                            handle = reopened
                            recordingHandle = reopened
                            runCatching { reopened.startRecording() }
                                .onFailure { error ->
                                    Log.e(TAG, "audio capture restart failed", error)
                                    failure = giveUp(run, "failed to restart AudioRecord")
                                }
                                .onSuccess { Log.i(TAG, "audio capture reopened") }
                        }
                    }

                    is CaptureReadAction.GiveUp -> {
                        Log.e(
                            TAG,
                            "audio capture read failed: ${action.reason}, ${diagnose(bytesRead, handle, policy, stillActive)}"
                        )
                        failure = giveUp(run, action.reason)
                    }
                }
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            markRunFinished(run)
        } catch (error: CancellationException) {
            Log.w(TAG, "audio capture cancelled")
            markRunFinished(run)
            if (!startSignal.isCompleted) {
                startSignal.completeExceptionally(error)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "audio capture failed", error)
            val wasCurrent = isCurrentRun(run)
            markRunFinished(run)
            if (!startSignal.isCompleted) {
                startSignal.completeExceptionally(error)
            } else if (wasCurrent) {
                failure = AudioCaptureException("audio capture failed", error)
            }
        } finally {
            handle.release()
        }
        failure?.let { runBlocking { onError(it) } }
    }

    // Encerra a captura corrente e devolve a falha que vai para o `onError` — nula se outra thread
    // já tinha encerrado esta captura (aí não há sessão para derrubar).
    private fun giveUp(run: Long, reason: String): AudioCaptureException? {
        if (!isCurrentRun(run)) return null
        markRunFinished(run)
        captureActive.set(false)
        return AudioCaptureException("audio capture read failed: $reason")
    }

    private fun isCurrentRun(run: Long): Boolean = captureActive.get() && activeRun.get() == run

    private fun markRunFinished(run: Long) {
        activeRun.compareAndSet(run, NO_RUN)
    }

    // Diagnóstico do achado P154: diz se o objeto morreu, se a gravação foi tomada, ou se foi só um
    // buffer vazio. Nunca carrega áudio nem texto ditado — só números de estado.
    private fun diagnose(
        bytesRead: Int,
        handle: RecordingHandle,
        policy: CaptureReadPolicy,
        stillActive: Boolean
    ): String =
        "code=$bytesRead, recordingState=${handle.recordingStateName()}, state=${handle.stateName()}, " +
            "emptyReads=${policy.consecutiveEmptyReads}, restarts=${policy.restartCount}, " +
            "captureActive=$stillActive, format=$format"

    private fun openRecord(channelMask: Int, recordBufferBytes: Int): AudioRecord {
        @Suppress("DEPRECATION")
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                format.sampleRate,
                channelMask,
                PlatformAudioFormat.ENCODING_PCM_16BIT,
                recordBufferBytes
            )
        } catch (error: Throwable) {
            throw AudioCaptureException("failed to create AudioRecord", error)
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            throw AudioCaptureException("AudioRecord failed to initialize")
        }
        return record
    }

    override fun stop() {
        val wasActive = captureActive.getAndSet(false)
        activeRun.set(NO_RUN)
        recordingHandle?.stop()
        captureThread?.let { thread ->
            if (thread !== Thread.currentThread()) {
                runCatching { thread.join(STOP_JOIN_TIMEOUT_MS) }
                    .onFailure { Log.w(TAG, "timed out waiting for audio capture thread", it) }
            }
        }
        recordingHandle?.release()
        recordingHandle = null
        captureThread = null
        if (wasActive) {
            Log.i(TAG, "audio capture stopped")
        }
    }

    private fun resolveChannelMask(channels: Int): Int = when (channels) {
        1 -> PlatformAudioFormat.CHANNEL_IN_MONO
        2 -> PlatformAudioFormat.CHANNEL_IN_STEREO
        else -> throw AudioCaptureException("unsupported channel count: $channels")
    }

    private class RecordingHandle(private val audioRecord: AudioRecord) {
        private val released = AtomicBoolean(false)

        fun isRecording(): Boolean =
            runCatching {
                audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING
            }.getOrDefault(false)

        fun startRecording() {
            audioRecord.startRecording()
        }

        fun read(buffer: ByteArray): Int = audioRecord.read(buffer, 0, buffer.size)

        fun recordingStateName(): String = when (runCatching { audioRecord.recordingState }.getOrNull()) {
            AudioRecord.RECORDSTATE_RECORDING -> "recording"
            AudioRecord.RECORDSTATE_STOPPED -> "stopped"
            else -> "unknown"
        }

        fun stateName(): String = when (runCatching { audioRecord.state }.getOrNull()) {
            AudioRecord.STATE_INITIALIZED -> "initialized"
            AudioRecord.STATE_UNINITIALIZED -> "uninitialized"
            else -> "unknown"
        }

        fun stop() {
            if (isRecording()) {
                runCatching { audioRecord.stop() }
                    .onFailure { Log.w(TAG, "failed to stop AudioRecord", it) }
            }
        }

        fun release() {
            if (released.compareAndSet(false, true)) {
                stop()
                runCatching { audioRecord.release() }
                    .onFailure { Log.w(TAG, "failed to release AudioRecord", it) }
            }
        }
    }

    private companion object {
        private const val TAG = "FlowVoiceDictation"
        private const val THREAD_NAME = "flowvoice-audio-capture"
        private const val START_TIMEOUT_MS = 3_000L
        private const val STOP_JOIN_TIMEOUT_MS = 1_000L
        private const val NO_RUN = -1L
    }
}
