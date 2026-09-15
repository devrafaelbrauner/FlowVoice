package dev.rafaelbrauner.flowvoice.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
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
import dev.rafaelbrauner.flowvoice.ui.overlay.BubbleHost
import dev.rafaelbrauner.flowvoice.ui.overlay.BubbleLayout
import dev.rafaelbrauner.flowvoice.ui.overlay.BubbleMove
import dev.rafaelbrauner.flowvoice.ui.overlay.BubblePlacement
import dev.rafaelbrauner.flowvoice.ui.overlay.BubblePoint
import dev.rafaelbrauner.flowvoice.ui.overlay.BubblePosition
import dev.rafaelbrauner.flowvoice.ui.overlay.DictationOverlay
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayLifecycleOwner
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayMode
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlaySessionPolicy
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayStartRequests
import dev.rafaelbrauner.flowvoice.ui.overlay.SafeArea
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
import java.util.Locale

class FlowVoiceOverlayService : Service(), KoinComponent, BubbleHost {
    private val pipeline by inject<DictationPipeline>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val positionStore by lazy { BubblePositionStore(this) }
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var mode = OverlayMode.Bubble
    private var barOffsetPx = -1
    private var keyboardTracking: Job? = null
    private var ownsSession = false
    private var position = BubblePosition.Default
    private var dragStart: BubblePoint? = null
    private var dragPoint: BubblePoint? = null
    private val layoutState = MutableStateFlow(BubbleLayout())

    override val layout: StateFlow<BubbleLayout> = layoutState.asStateFlow()

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mode != OverlayMode.Bar) placeBubble()
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

    override fun onDrag(dx: Float, dy: Float) {
        val manager = windowManager ?: return
        val area = safeArea(manager)
        val start = dragStart ?: BubblePlacement.pointOf(position, area, bubbleSize()).also { dragStart = it }
        val startedNow = dragPoint == null
        dragPoint = BubblePlacement.dragged(start, dx, dy, area, bubbleSize())
        if (mode == OverlayMode.Bar) return
        updateLayout(bubbleParams(manager))
        if (startedNow) publishLayout(manager)
    }

    override fun onDragEnd() {
        val manager = windowManager
        val point = dragPoint
        dragStart = null
        dragPoint = null
        if (manager == null || point == null) return
        position = BubblePlacement.snap(point, safeArea(manager), bubbleSize())
        savePosition("drag")
        placeBubble()
    }

    override fun onMove(move: BubbleMove) {
        position = BubblePlacement.moved(position, move)
        savePosition("action")
        placeBubble()
    }

    private fun savePosition(source: String) {
        positionStore.write(position)
        val fraction = String.format(Locale.ROOT, "%.2f", position.fraction)
        Log.i(TAG, "overlay_bubble_moved source=$source side=${position.encodedSide} fraction=$fraction")
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
            .setContentText("Toque na bolha para ditar no campo aberto; arraste para mudá-la de lugar.")
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
        position = positionStore.read()
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        owner.attachTo(view)
        view.setContent {
            DictationOverlay(
                pipeline = pipeline,
                host = this,
                onModeChange = ::applyMode,
                onSessionOwned = { ownsSession = true }
            )
        }
        try {
            manager.addView(view, bubbleParams(manager))
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
        publishLayout(manager)
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
        if (next != OverlayMode.Bar) {
            barOffsetPx = -1
            placeBubble()
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

    // Bolha sozinha, ou bolha com a prévia da sessão direta (P139). No arraste, só a bolha.
    private fun placeBubble() {
        val manager = windowManager ?: return
        val params = if (mode == OverlayMode.Preview && dragPoint == null) previewParams(manager) else bubbleParams(manager)
        updateLayout(params)
        publishLayout(manager)
    }

    private fun publishLayout(manager: WindowManager) {
        val preview = BubblePlacement.previewWindow(
            position,
            safeArea(manager),
            bubbleSize(),
            previewMaxWidth(),
            screenHeight(manager)
        )
        layoutState.value = BubbleLayout(side = position.side, growsUp = preview.fromBottom, dragging = dragPoint != null)
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

    private fun bubbleParams(manager: WindowManager): WindowManager.LayoutParams {
        val area = safeArea(manager)
        val point = dragPoint ?: BubblePlacement.pointOf(position, area, bubbleSize())
        return screenParams(WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = point.x
            y = point.y
        }
    }

    private fun previewParams(manager: WindowManager): WindowManager.LayoutParams {
        val window = BubblePlacement.previewWindow(
            position,
            safeArea(manager),
            bubbleSize(),
            previewMaxWidth(),
            screenHeight(manager)
        )
        return screenParams(window.width).apply {
            gravity = (if (window.fromBottom) Gravity.BOTTOM else Gravity.TOP) or Gravity.START
            x = window.x
            y = window.y
        }
    }

    // x/y em pixels da tela: sem encaixe automático nas barras do sistema, que a SafeArea já desconta.
    private fun screenParams(width: Int) = overlayParams(
        width = width,
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
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

    private fun safeArea(manager: WindowManager): SafeArea {
        val margin = dp(BUBBLE_EDGE_MARGIN_DP)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = manager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            SafeArea(
                left = bounds.left + insets.left + margin,
                top = bounds.top + insets.top + margin,
                right = bounds.right - insets.right - margin,
                bottom = bounds.bottom - insets.bottom - margin
            )
        } else {
            val display = resources.displayMetrics
            SafeArea(
                left = margin,
                top = dp(FALLBACK_STATUS_INSET_DP) + margin,
                right = display.widthPixels - margin,
                bottom = display.heightPixels - dp(FALLBACK_NAVIGATION_INSET_DP) - margin
            )
        }
    }

    private fun bubbleSize(): Int = dp(BUBBLE_SIZE_DP)

    private fun previewMaxWidth(): Int = dp(PREVIEW_MAX_WIDTH_DP) + bubbleSize()

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
        private const val BUBBLE_EDGE_MARGIN_DP = 12
        private const val PREVIEW_MAX_WIDTH_DP = 360
        private const val FALLBACK_STATUS_INSET_DP = 24
        private const val FALLBACK_NAVIGATION_INSET_DP = 48
        private const val OVERLAY_UNAVAILABLE_MESSAGE =
            "Botão flutuante indisponível: permita sobrepor a outros apps (Android 8+)."
        const val ACTION_STOP = "dev.rafaelbrauner.flowvoice.action.STOP_OVERLAY"
        const val BUBBLE_SIZE_DP = 76

        private val runningState = MutableStateFlow(false)
        val runningFlow: StateFlow<Boolean> = runningState.asStateFlow()

        val running: Boolean
            get() = runningState.value
    }
}
