package dev.rafaelbrauner.flowvoice.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.rafaelbrauner.flowvoice.MainActivity
import dev.rafaelbrauner.flowvoice.shared.overlay.OverlayStartGuard
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FlowVoiceOverlayService : Service(), KoinComponent {
    private val pipeline by inject<DictationPipeline>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var button: Button? = null
    private var startedHere = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (button == null && !showOverlay()) {
            stopOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (startedHere && pipeline.status.value.isBusy) {
            pipeline.requestCancel()
        }
        scope.cancel()
        button?.takeIf { it.isAttachedToWindow }?.let { windowManager?.removeView(it) }
        button = null
        windowManager = null
        runningState.value = false
        super.onDestroy()
    }

    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundType())
        true
    } catch (error: RuntimeException) {
        Log.w(TAG, "overlay_foreground_denied ${error.javaClass.simpleName}")
        false
    }

    private fun stopOverlay() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0

    private fun buildNotification() = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Botão de ditado", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, FlowVoiceOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("FlowVoice pronto para ditar")
            .setContentText("Toque no botão flutuante para gravar; toque de novo para inserir.")
            .setContentIntent(open)
            .addAction(0, "Ocultar botão", stop)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay(): Boolean {
        if (!OverlayStartGuard.canShow(Build.VERSION.SDK_INT, Settings.canDrawOverlays(this))) {
            Log.w(TAG, "overlay_not_allowed")
            Toast.makeText(this, OVERLAY_UNAVAILABLE_MESSAGE, Toast.LENGTH_LONG).show()
            return false
        }
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlay = Button(this).apply {
            setOnClickListener { onTap() }
            setOnLongClickListener {
                if (pipeline.status.value.isBusy) {
                    pipeline.requestCancel()
                }
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 48
            y = 180
        }
        try {
            manager.addView(overlay, params)
        } catch (error: RuntimeException) {
            Log.w(TAG, "overlay_add_failed ${error.javaClass.simpleName}")
            Toast.makeText(this, OVERLAY_UNAVAILABLE_MESSAGE, Toast.LENGTH_LONG).show()
            return false
        }
        windowManager = manager
        button = overlay
        runningState.value = true
        render(pipeline.status.value)
        scope.launch {
            pipeline.status.drop(1).collect { status ->
                render(status)
                report(status)
            }
        }
        return true
    }

    private fun onTap() {
        when (pipeline.status.value) {
            DictationPipelineStatus.Recording -> pipeline.requestFinalize()
            DictationPipelineStatus.Starting,
            DictationPipelineStatus.Transcribing -> Unit
            else -> {
                startedHere = true
                pipeline.requestStart()
            }
        }
    }

    private fun render(status: DictationPipelineStatus) {
        button?.text = when (status) {
            DictationPipelineStatus.Starting -> "Iniciando…"
            DictationPipelineStatus.Recording -> "● Gravando"
            DictationPipelineStatus.Transcribing -> "Transcrevendo…"
            else -> "Ditar"
        }
    }

    private fun report(status: DictationPipelineStatus) {
        val message = when (status) {
            is DictationPipelineStatus.Completed -> listOfNotNull(
                status.warning,
                if (status.insertion.success) null else "Texto não inserido: ${status.insertion.summary}"
            ).joinToString("\n").ifEmpty { null }
            is DictationPipelineStatus.Failed -> "Falha no ditado: ${status.message}"
            DictationPipelineStatus.Cancelled -> "Ditado cancelado"
            else -> null
        }
        Log.i(TAG, "overlay_status ${status::class.simpleName}")
        message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "FlowVoiceOverlay"
        private const val CHANNEL_ID = "flowvoice_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val OVERLAY_UNAVAILABLE_MESSAGE =
            "Botão flutuante indisponível: permita sobrepor a outros apps (Android 8+)."
        const val ACTION_STOP = "dev.rafaelbrauner.flowvoice.action.STOP_OVERLAY"

        private val runningState = MutableStateFlow(false)
        val runningFlow: StateFlow<Boolean> = runningState.asStateFlow()

        val running: Boolean
            get() = runningState.value
    }
}
