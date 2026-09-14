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
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.rafaelbrauner.flowvoice.MainActivity
import dev.rafaelbrauner.flowvoice.shared.overlay.OverlayStartGuard
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.ui.overlay.DictationOverlay
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayLifecycleOwner
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayMode
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlaySessionPolicy
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayStartRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FlowVoiceOverlayService : Service(), KoinComponent {
    private val pipeline by inject<DictationPipeline>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var mode = OverlayMode.Bubble
    private var barOffsetPx = -1
    private var keyboardTracking: Job? = null
    private var ownsSession = false

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
        if (overlayView == null && !showOverlay()) {
            stopOverlay()
            return START_NOT_STICKY
        }
        if (intent?.action == OverlayStartRequests.ACTION_START_DICTATION) {
            OverlayStartRequests.request()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (OverlaySessionPolicy.cancelOnDestroy(ownsSession, pipeline.target.value, pipeline.status.value)) {
            pipeline.requestCancel()
        }
        keyboardTracking?.cancel()
        scope.cancel()
        overlayView?.let { view ->
            view.disposeComposition()
            if (view.isAttachedToWindow) {
                windowManager?.removeView(view)
            }
        }
        lifecycleOwner?.destroy()
        overlayView = null
        lifecycleOwner = null
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
            .setContentText("Toque no microfone para ditar; toque em Inserir para enviar o texto.")
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
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        owner.attachTo(view)
        view.setContent {
            DictationOverlay(
                pipeline = pipeline,
                onModeChange = ::applyMode,
                onSessionOwned = { ownsSession = true }
            )
        }
        try {
            manager.addView(view, bubbleParams())
        } catch (error: RuntimeException) {
            Log.w(TAG, "overlay_add_failed ${error.javaClass.simpleName}")
            owner.destroy()
            Toast.makeText(this, OVERLAY_UNAVAILABLE_MESSAGE, Toast.LENGTH_LONG).show()
            return false
        }
        windowManager = manager
        overlayView = view
        lifecycleOwner = owner
        mode = OverlayMode.Bubble
        runningState.value = true
        scope.launch {
            pipeline.status.drop(1).collect { status ->
                ownsSession = OverlaySessionPolicy.ownedAfter(ownsSession, status)
                Log.i(TAG, "overlay_status ${status::class.simpleName}")
            }
        }
        return true
    }

    private fun applyMode(next: OverlayMode) {
        if (next == mode) return
        mode = next
        keyboardTracking?.cancel()
        keyboardTracking = null
        if (next == OverlayMode.Bubble) {
            barOffsetPx = -1
            updateLayout(bubbleParams())
            return
        }
        placeBar()
        keyboardTracking = scope.launch {
            while (isActive) {
                delay(KEYBOARD_POLL_MS)
                placeBar()
            }
        }
    }

    private fun placeBar() {
        val offset = barOffset()
        if (offset == barOffsetPx) return
        barOffsetPx = offset
        updateLayout(barParams(offset))
    }

    private fun updateLayout(params: WindowManager.LayoutParams) {
        val view = overlayView ?: return
        if (!view.isAttachedToWindow) return
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (error: RuntimeException) {
            Log.w(TAG, "overlay_layout_failed ${error.javaClass.simpleName}")
        }
    }

    private fun bubbleParams() = overlayParams(
        width = WindowManager.LayoutParams.WRAP_CONTENT,
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        x = dp(BUBBLE_MARGIN_END_DP)
        y = dp(BUBBLE_MARGIN_BOTTOM_DP)
    }

    private fun barParams(offset: Int) = overlayParams(
        width = WindowManager.LayoutParams.MATCH_PARENT,
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.START
        x = 0
        y = offset
    }

    private fun overlayParams(width: Int, flags: Int) = WindowManager.LayoutParams(
        width,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    )

    private fun barOffset(): Int {
        val manager = windowManager ?: return 0
        val screenHeight = screenHeight(manager)
        val keyboardTop = FlowVoiceAccessibilityService.service?.inputMethodTopOnScreen()
        if (keyboardTop != null && keyboardTop in 1 until screenHeight) {
            return screenHeight - keyboardTop
        }
        return navigationBarInset(manager)
    }

    private fun screenHeight(manager: WindowManager): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.currentWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }

    private fun navigationBarInset(manager: WindowManager): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            dp(FALLBACK_NAVIGATION_INSET_DP)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "FlowVoiceOverlay"
        private const val CHANNEL_ID = "flowvoice_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val KEYBOARD_POLL_MS = 400L
        private const val BUBBLE_MARGIN_END_DP = 12
        private const val BUBBLE_MARGIN_BOTTOM_DP = 96
        private const val FALLBACK_NAVIGATION_INSET_DP = 48
        private const val OVERLAY_UNAVAILABLE_MESSAGE =
            "Botão flutuante indisponível: permita sobrepor a outros apps (Android 8+)."
        const val ACTION_STOP = "dev.rafaelbrauner.flowvoice.action.STOP_OVERLAY"

        private val runningState = MutableStateFlow(false)
        val runningFlow: StateFlow<Boolean> = runningState.asStateFlow()

        val running: Boolean
            get() = runningState.value
    }
}
