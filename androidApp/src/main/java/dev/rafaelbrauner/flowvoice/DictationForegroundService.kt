package dev.rafaelbrauner.flowvoice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import dev.rafaelbrauner.flowvoice.shared.audio.AndroidAudioCapture
import dev.rafaelbrauner.flowvoice.shared.audio.VadConfig
import dev.rafaelbrauner.flowvoice.shared.security.AndroidApiKeyStore
import dev.rafaelbrauner.flowvoice.shared.session.ChannelStreamRouter
import dev.rafaelbrauner.flowvoice.shared.session.DictationConfig
import dev.rafaelbrauner.flowvoice.shared.session.SessionState
import dev.rafaelbrauner.flowvoice.shared.session.StreamRouter
import dev.rafaelbrauner.flowvoice.shared.session.StreamText
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionManager
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DictationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var capture: AndroidAudioCapture? = null
    private var manager: TranscriptionManager? = null
    private var router: StreamRouter? = null
    private var previewJob: Job? = null
    private var previewSeq = 0L
    private var previewCalls = 0
    private var previewErrors = 0
    private var previewAudioMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        mutableState.value = SessionState.IDLE
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDictation()
            ACTION_STOP -> {
                serviceScope.launch { stopAndTranscribe() }
            }
            ACTION_CANCEL -> {
                serviceScope.launch { cancelDictation() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        capture = null
        manager = null
        router = null
        previewJob = null
        instance = null
        mutableState.value = SessionState.IDLE
        super.onDestroy()
    }

    private fun startDictation() {
        if (router?.isOpen == true) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO negado — peça permissão na tela principal")
            mutableError.value = "Permissão de microfone negada"
            return
        }
        startForegroundWithNotification()
        val streamRouter = ChannelStreamRouter()
        router = streamRouter
        mutableError.value = null
        val transcriptionManager = TranscriptionManager(
            router = streamRouter,
            api = ServiceLocator.transcriptionApi(this),
            config = DictationConfig(),
            scope = serviceScope
        )
        manager = transcriptionManager
        mutableState.value = SessionState.LISTENING
        val audioCapture = AndroidAudioCapture(
            config = VadConfig(),
            onFrame = { frame -> streamRouter.feed(frame) },
            scope = serviceScope
        )
        capture = audioCapture
        transcriptionManager.start()
        if (!audioCapture.start()) {
            Log.e(TAG, "AudioRecord não iniciou")
            mutableError.value = "Microfone indisponível"
            serviceScope.launch { cancelDictation() }
        } else {
            Log.i(TAG, "Ditado iniciado")
            startPreviewLoop(transcriptionManager)
        }
    }

    private fun startPreviewLoop(transcriptionManager: TranscriptionManager) {
        previewJob?.cancel()
        previewSeq += 1
        val seq = previewSeq
        mutableLiveText.value = StreamText("", "")
        previewJob = serviceScope.launch(Dispatchers.IO) {
            previewCalls = 0
            previewErrors = 0
            previewAudioMs = 0L
            val key = AndroidApiKeyStore(this@DictationForegroundService).loadApiKey()
            if (key.isNullOrBlank()) return@launch
            val model = SharedPrefsModelStore(this@DictationForegroundService).loadModel()
                ?: TranscriptionManager.DEFAULT_MODEL
            val transcriber = ServiceLocator.transcriptionApi(this@DictationForegroundService)
            var consecutiveErrors = 0
            while (seq == previewSeq && router?.isOpen == true) {
                kotlinx.coroutines.delay(PREVIEW_INTERVAL_MS)
                if (seq != previewSeq || router?.isOpen != true) break
                val audio = try {
                    transcriptionManager.snapshot()
                } catch (_: Exception) {
                    break
                }
                if (audio.size < MIN_PREVIEW_SAMPLES) continue
                val tail = audio.copyOfRange(
                    maxOf(0, audio.size - PREVIEW_WINDOW_SAMPLES),
                    audio.size
                )
                try {
                    val wav = dev.rafaelbrauner.flowvoice.shared.api.OpenRouterTranscriptionClient
                        .wavBytes(tail, PREVIEW_SAMPLE_RATE)
                    val result = transcriber.transcribe(
                        dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionRequest(
                            audio = wav,
                            model = model,
                            language = "pt"
                        ),
                        key
                    )
                    consecutiveErrors = 0
                    previewCalls += 1
                    previewAudioMs += (tail.size * 1000L / PREVIEW_SAMPLE_RATE)
                    if (seq == previewSeq && result.text.isNotBlank()) {
                        transcriptionManager.publishTentative(result.text)
                        mutableLiveText.value = transcriptionManager.liveText.value
                    }
                } catch (e: Exception) {
                    consecutiveErrors += 1
                    previewErrors += 1
                    if (consecutiveErrors >= MAX_PREVIEW_ERRORS) {
                        Log.w(TAG, "Prévia pausada após $consecutiveErrors erros: ${e.message}")
                        break
                    }
                }
            }
        }
    }

    private suspend fun stopAndTranscribe() {
        val transcriptionManager = manager
        if (transcriptionManager == null) {
            stopSelf()
            return
        }
        mutableState.value = SessionState.WORKING
        val key = AndroidApiKeyStore(this@DictationForegroundService).loadApiKey()
        if (key.isNullOrBlank()) {
            capture?.stop()
            transcriptionManager.cancel()
            manager = null
            capture = null
            router = null
            Log.i(TAG, "STOP sem chave — sessão descartada sem rede")
            mutableError.value = "Chave da API não configurada"
            mutableState.value = SessionState.IDLE
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        try {
            previewSeq += 1
            previewJob?.cancel()
            previewJob = null
            capture?.stop()
            val model = SharedPrefsModelStore(this@DictationForegroundService).loadModel()
                ?: dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionManager.DEFAULT_MODEL
            val (text, report) = transcribeWithModel(transcriptionManager, key, model)
            if (text.isNotBlank()) {
                val service = FlowVoiceAccessibilityService.service
                if (service != null) {
                    val result = service.insertDirect(text)
                    Log.i(TAG, "Inserção: ${result.summary} modelo=$model janelas=${report?.windows} " +
                        "latência=${report?.totalLatencyMs}ms prévia=${previewCalls}x/${previewAudioMs}ms/${previewErrors}e")
                    mutableLastResult.value = text
                    mutableLastReport.value = report
                    mutableLastModel.value = model
                } else {
                    mutableError.value = "Serviço de acessibilidade inativo — texto: $text"
                }
            } else {
                mutableError.value = "Nada transcrito"
            }
        } catch (e: Exception) {
            mutableError.value = e.message ?: "Falha na transcrição"
        } finally {
            previewSeq += 1
            previewJob?.cancel()
            previewJob = null
            manager = null
            capture = null
            router = null
            mutableState.value = SessionState.IDLE
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun cancelDictation() {
        previewSeq += 1
        previewJob?.cancel()
        previewJob = null
        manager?.cancel()
        capture?.stop()
        manager = null
        capture = null
        router = null
        mutableState.value = SessionState.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundWithNotification() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ditado FlowVoice",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("FlowVoice ouvindo")
            .setContentText("Toque para voltar ao app")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun transcribeWithModel(
        transcriptionManager: dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionManager,
        key: String,
        model: String
    ): Pair<String, dev.rafaelbrauner.flowvoice.shared.transcription.IncrementalReport?> =
        transcriptionManager.stop(key, model)

    companion object {
        const val TAG = "FlowVoiceDict"
        const val ACTION_START = "dev.rafaelbrauner.flowvoice.START"
        const val ACTION_STOP = "dev.rafaelbrauner.flowvoice.STOP"
        const val ACTION_CANCEL = "dev.rafaelbrauner.flowvoice.CANCEL"
        private const val CHANNEL_ID = "flowvoice_dictation"
        private const val NOTIFICATION_ID = 41
        private const val PREVIEW_INTERVAL_MS = 3000L
        private const val PREVIEW_SAMPLE_RATE = 16000
        private const val PREVIEW_WINDOW_SAMPLES = PREVIEW_SAMPLE_RATE * 6
        private const val MIN_PREVIEW_SAMPLES = PREVIEW_SAMPLE_RATE * 2
        private const val MAX_PREVIEW_ERRORS = 3

        private val mutableState = MutableStateFlow(SessionState.IDLE)
        val state: StateFlow<SessionState> = mutableState.asStateFlow()

        private val mutableLiveText =
            MutableStateFlow(dev.rafaelbrauner.flowvoice.shared.session.StreamText("", ""))
        val liveText: StateFlow<dev.rafaelbrauner.flowvoice.shared.session.StreamText> =
            mutableLiveText.asStateFlow()

        private val mutableError = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = mutableError.asStateFlow()

        private val mutableLastResult = MutableStateFlow("")
        val lastResult: StateFlow<String> = mutableLastResult.asStateFlow()

        private val mutableLastReport =
            MutableStateFlow<dev.rafaelbrauner.flowvoice.shared.transcription.IncrementalReport?>(null)
        val lastReport: StateFlow<dev.rafaelbrauner.flowvoice.shared.transcription.IncrementalReport?> =
            mutableLastReport.asStateFlow()

        private val mutableLastModel = MutableStateFlow("")
        val lastModel: StateFlow<String> = mutableLastModel.asStateFlow()

        var instance: DictationForegroundService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, DictationForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, DictationForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, DictationForegroundService::class.java).apply {
                    action = ACTION_CANCEL
                }
            )
        }
    }
}
