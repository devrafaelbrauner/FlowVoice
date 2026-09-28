package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlayStartPlan(val showBubble: Boolean, val persistEnabled: Boolean, val requestDictation: Boolean)

object OverlayStartRequests {
    const val ACTION_START_DICTATION = "dev.rafaelbrauner.flowvoice.action.START_DICTATION"

    private val requested = MutableStateFlow(0)
    private var handled = 0

    val count: StateFlow<Int> = requested.asStateFlow()

    /**
     * Só o interruptor dos Ajustes e o religar da P141 ligam a bolha e guardam "bolha ligada". O microfone
     * do Início usa o overlay só para aquele ditado (Y2): com a bolha desligada, a barra ou a prévia
     * aparecem sem a bolha, nada é gravado, e o serviço encerra quando o ditado acaba.
     */
    fun plan(action: String?, bubbleEnabled: Boolean, bubbleOnScreen: Boolean): OverlayStartPlan =
        if (action == ACTION_START_DICTATION) {
            OverlayStartPlan(showBubble = bubbleEnabled || bubbleOnScreen, persistEnabled = false, requestDictation = true)
        } else {
            OverlayStartPlan(showBubble = true, persistEnabled = true, requestDictation = false)
        }

    fun request() {
        requested.value += 1
    }

    fun takePending(): Boolean {
        val current = requested.value
        if (current <= handled) return false
        handled = current
        return true
    }
}
