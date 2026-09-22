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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.rafaelbrauner.flowvoice.MainActivity
import dev.rafaelbrauner.flowvoice.shared.overlay.OverlayStartGuard
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.ui.overlay.BubbleHost
import dev.rafaelbrauner.flowvoice.ui.overlay.BubbleMove
import dev.rafaelbrauner.flowvoice.ui.overlay.BubblePlacement
import dev.rafaelbrauner.flowvoice.ui.overlay.BubblePoint
import dev.rafaelbrauner.flowvoice.ui.overlay.BubblePosition
import dev.rafaelbrauner.flowvoice.ui.overlay.DictationOverlay
import dev.rafaelbrauner.flowvoice.ui.overlay.DirectPreviewState
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayLifecycleOwner
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayMode
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlaySessionPolicy
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayStartRequests
import dev.rafaelbrauner.flowvoice.ui.overlay.PreviewActions
import dev.rafaelbrauner.flowvoice.ui.overlay.PreviewCardOverlay
import dev.rafaelbrauner.flowvoice.ui.overlay.PreviewCardUi
import dev.rafaelbrauner.flowvoice.ui.overlay.PreviewPlacement
import dev.rafaelbrauner.flowvoice.ui.overlay.PreviewSpace
import dev.rafaelbrauner.flowvoice.ui.overlay.SafeArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

class FlowVoiceOverlayService : Service(), KoinComponent, BubbleHost {
    private val pipeline by inject<DictationPipeline>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val positionStore by lazy { BubblePositionStore(this) }
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var cardView: ComposeView? = null
    private var cardAttached = false
    private var cardTracking: Job? = null
    private val cardRefresh = Channel<Unit>(Channel.CONFLATED)
    private val cardUi = MutableStateFlow(PreviewCardUi())
    private var previewState: DirectPreviewState = DirectPreviewState.Hidden
    private var previewActions: PreviewActions? = null
    private var lastPlacementLog: String? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var mode = OverlayMode.Bubble
    private var barOffsetPx = -1
    private var keyboardTracking: Job? = null
    private var ownsSession = false
    private val bubbleHiddenState = MutableStateFlow(false)
    private var position = BubblePosition.Default
    private var dragStart: BubblePoint? = null
    private var dragPoint: BubblePoint? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            positionStore.writeEnabled(false)
            hideBubble()
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
        // Guardado para o app religar a bolha depois de uma reinstalação ou atualização (P141).
        positionStore.writeEnabled(true)
        showBubble()
        if (intent?.action == OverlayStartRequests.ACTION_START_DICTATION) {
            OverlayStartRequests.request()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mode != OverlayMode.Bar) placeBubble()
        syncCard()
    }

    override fun onDestroy() {
        if (OverlaySessionPolicy.cancelOnDestroy(ownsSession, pipeline.target.value, pipeline.status.value)) {
            pipeline.requestCancel()
        }
        keyboardTracking?.cancel()
        hideCard()
        scope.cancel()
        overlayView?.let { view ->
            view.disposeComposition()
            if (view.isAttachedToWindow) {
                windowManager?.removeView(view)
            }
        }
        lifecycleOwner?.destroy()
        overlayView = null
        cardView = null
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
        if (startedNow) hideCard()
        if (mode == OverlayMode.Bar) return
        updateLayout(bubbleParams(manager))
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
        syncCard()
    }

    override fun onMove(move: BubbleMove) {
        position = BubblePlacement.moved(position, move)
        savePosition("action")
        placeBubble()
        syncCard()
    }

    override fun updatePreview(state: DirectPreviewState, actions: PreviewActions) {
        if (state == previewState && actions === previewActions) return
        val contentChanged = withoutClock(state) != withoutClock(previewState)
        previewState = state
        previewActions = actions
        cardUi.value = cardUi.value.copy(state = state, actions = actions)
        syncCard(reposition = contentChanged)
    }

    private fun withoutClock(state: DirectPreviewState): DirectPreviewState =
        (state as? DirectPreviewState.Live)?.copy(clock = "", transcribing = false) ?: state

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

    // "Ocultar botão" tira só a bolha da tela: com a barra ou a prévia visíveis o ditado em andamento
    // continua com os controles à mão (P159) e o serviço só morre quando não sobra nada a mostrar. O
    // `running` é a bolha na tela, não o serviço vivo — é o que o interruptor dos Ajustes mostra.
    private fun hideBubble() {
        bubbleHiddenState.value = true
        runningState.value = false
        if (OverlaySessionPolicy.shouldStopWithHiddenBubble(bubbleHiddenState.value, mode)) stopSelf()
    }

    private fun showBubble() {
        bubbleHiddenState.value = false
        runningState.value = true
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
                bubbleHidden = bubbleHiddenState,
                onModeChange = ::applyMode,
                onSessionOwned = { ownership -> ownsSession = OverlaySessionPolicy.ownsSession(ownership) }
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
        if (OverlaySessionPolicy.shouldStopWithHiddenBubble(bubbleHiddenState.value, next)) {
            stopSelf()
            return
        }
        if (next == mode) return
        mode = next
        keyboardTracking?.cancel()
        keyboardTracking = null
        if (next != OverlayMode.Bar) {
            barOffsetPx = -1
            placeBubble()
            syncCard()
            return
        }
        hideCard()
        placeBar()
        keyboardTracking = scope.launch {
            while (isActive) {
                delay(KEYBOARD_POLL_MS)
                placeBar()
            }
        }
    }

    private fun placeBubble() {
        val manager = windowManager ?: return
        updateLayout(bubbleParams(manager))
    }

    private fun cardWanted(): Boolean =
        mode == OverlayMode.Preview && dragPoint == null && previewState != DirectPreviewState.Hidden

    private fun syncCard(reposition: Boolean = true) {
        if (!cardWanted()) {
            hideCard()
            return
        }
        if (cardTracking?.isActive != true) {
            cardTracking = scope.launch { trackCard() }
        } else if (reposition) {
            cardRefresh.trySend(Unit)
        }
    }

    // A posição do cursor só é lida com a prévia visível: ao abrir, quando o conteúdo muda (trecho digitado,
    // pausa, resultado) e a cada segundo, o que também pega o teclado abrindo ou fechando. A leitura traz só
    // coordenadas e roda na thread principal: fora dela, no S26, o nó com foco não vinha (caret e campo nulos).
    private suspend fun trackCard() {
        while (true) {
            placeCard(readCardGeometry())
            withTimeoutOrNull(CARD_REFRESH_MS) { cardRefresh.receive() }
        }
    }

    private fun readCardGeometry(): CardGeometry {
        val service = FlowVoiceAccessibilityService.service ?: return CardGeometry(null, null)
        val focus = try {
            service.focusGeometry()
        } catch (error: RuntimeException) {
            Log.w(TAG, "overlay_focus_geometry_failed ${error.javaClass.simpleName}")
            null
        }
        return CardGeometry(focus = focus, keyboardTop = runCatching { service.inputMethodTopOnScreen() }.getOrNull())
    }

    private fun placeCard(geometry: CardGeometry) {
        val manager = windowManager ?: return
        if (!cardWanted()) return
        val card = cardView ?: createCardView()?.also { cardView = it } ?: return
        val area = safeArea(manager)
        val size = bubbleSize()
        val bubble = BubblePlacement.pointOf(position, area, size)
        val screenHeight = screenHeight(manager)
        val gap = dp(CARD_GAP_DP)
        val keyboardTop = geometry.keyboardTop?.takeIf { it in (area.top + 1) until screenHeight }
        val space = PreviewSpace(top = area.top, bottom = keyboardTop?.let { minOf(area.bottom, it - gap) } ?: area.bottom)
        val placement = PreviewPlacement.vertical(
            space = space,
            bubbleTop = bubble.y,
            bubbleSize = size,
            bubbleInLowerHalf = position.fraction > 0.5f,
            caret = geometry.focus?.caret,
            field = geometry.focus?.field,
            desiredHeight = dp(desiredCardHeightDp(previewState)),
            minHeight = dp(CARD_MIN_HEIGHT_DP),
            margin = dp(CARD_CARET_MARGIN_DP)
        )
        if (placement == null) {
            logPlacement("overlay_preview_hidden reason=no_room caret=${geometry.focus?.caret} keyboardTop=$keyboardTop")
            detachCard()
            return
        }
        val span = PreviewPlacement.horizontal(
            side = position.side,
            bubbleX = bubble.x,
            bubbleSize = size,
            areaLeft = area.left,
            areaRight = area.right,
            gap = gap,
            maxWidth = dp(PREVIEW_MAX_WIDTH_DP)
        )
        val params = screenParams(span.width).apply {
            gravity = (if (placement.fromBottom) Gravity.BOTTOM else Gravity.TOP) or Gravity.START
            x = span.x
            y = if (placement.fromBottom) screenHeight - placement.edge else placement.edge
        }
        cardUi.value = cardUi.value.copy(maxHeightPx = placement.maxHeight, compact = placement.compact)
        val opens = if (placement.fromBottom) "above" else "below"
        logPlacement(
            "overlay_preview_placed avoiding=${placement.avoiding.name.lowercase()} opens=$opens edge=${placement.edge} " +
                "maxHeight=${placement.maxHeight} compact=${placement.compact} caret=${geometry.focus?.caret} keyboardTop=$keyboardTop"
        )
        try {
            if (cardAttached) {
                manager.updateViewLayout(card, params)
            } else {
                manager.addView(card, params)
                cardAttached = true
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "overlay_card_layout_failed ${error.javaClass.simpleName}")
        }
    }

    // Coordenadas apenas; o log só sai quando a escolha muda, não a cada segundo.
    private fun logPlacement(line: String) {
        if (line == lastPlacementLog) return
        lastPlacementLog = line
        Log.i(TAG, line)
    }

    // Estimativa da altura do cartão para escolher o lado; a altura real nunca passa do maxHeight escolhido.
    private fun desiredCardHeightDp(state: DirectPreviewState): Int = when (state) {
        is DirectPreviewState.Live -> CARD_BASE_DP +
            (if (state.typedTail.isNotEmpty()) CARD_TEXT_DP else 0) +
            (if (state.notice != null) CARD_NOTICE_DP else 0) +
            (if (state.warning != null) CARD_WARNING_DP else 0) +
            (if (state.pending.isNotEmpty()) CARD_PENDING_DP else 0)
        is DirectPreviewState.Result -> CARD_RESULT_DP
        DirectPreviewState.Hidden -> 0
    }

    private fun hideCard() {
        cardTracking?.cancel()
        cardTracking = null
        lastPlacementLog = null
        detachCard()
    }

    // Um ComposeView novo a cada abertura: recolocado na janela depois de removido, o mesmo view ficava com o
    // último quadro composto (cartão vazio, sem conteúdo) no S26.
    private fun createCardView(): ComposeView? {
        val owner = lifecycleOwner ?: return null
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { PreviewCardOverlay(cardUi) }
        }
    }

    private fun detachCard() {
        val card = cardView ?: return
        cardView = null
        if (cardAttached) {
            cardAttached = false
            try {
                windowManager?.removeView(card)
            } catch (error: RuntimeException) {
                Log.w(TAG, "overlay_card_remove_failed ${error.javaClass.simpleName}")
            }
        }
        card.disposeComposition()
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

    private class CardGeometry(
        val focus: FlowVoiceAccessibilityService.FocusGeometry?,
        val keyboardTop: Int?
    )

    companion object {
        private const val TAG = "FlowVoiceOverlay"
        private const val CHANNEL_ID = "flowvoice_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val KEYBOARD_POLL_MS = 400L
        private const val CARD_REFRESH_MS = 1_000L
        private const val BUBBLE_EDGE_MARGIN_DP = 12
        private const val PREVIEW_MAX_WIDTH_DP = 360
        private const val CARD_GAP_DP = 8
        private const val CARD_CARET_MARGIN_DP = 12
        private const val CARD_MIN_HEIGHT_DP = 96
        private const val CARD_BASE_DP = 104
        private const val CARD_TEXT_DP = 44
        private const val CARD_NOTICE_DP = 52
        private const val CARD_WARNING_DP = 36
        private const val CARD_PENDING_DP = 64
        private const val CARD_RESULT_DP = 72
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
