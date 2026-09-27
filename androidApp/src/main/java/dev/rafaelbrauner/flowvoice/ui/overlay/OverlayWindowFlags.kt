package dev.rafaelbrauner.flowvoice.ui.overlay

import android.view.WindowManager

enum class OverlayWindow { Bubble, Card }

/**
 * Flags de cada janela do overlay. A da bolha (que também vira a barra) fica intocável quando está GONE,
 * para não sobrar superfície que engula toques (P164). O cartão da prévia é outra janela e nunca herda
 * isso: com a bolha oculta ele é o único controle do ditado direto — "Encerrar", "Cancelar" e "Inserir
 * aqui" (P159) —, e intocável deixaria o microfone aberto sem saída.
 */
object OverlayWindowFlags {
    fun touchable(window: OverlayWindow, bubbleWindowShown: Boolean): Boolean = when (window) {
        OverlayWindow.Bubble -> bubbleWindowShown
        OverlayWindow.Card -> true
    }

    fun flags(window: OverlayWindow, bubbleWindowShown: Boolean): Int {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return if (touchable(window, bubbleWindowShown)) base else base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
}
