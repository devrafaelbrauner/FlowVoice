package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.math.hypot

// Toque ou arraste da bolha (P138), com coordenadas brutas da tela: a janela anda com o dedo, então
// coordenadas locais mudariam a cada passo. Depois de passar do slop o gesto é arraste até o fim, e
// soltar nunca vira toque.
class BubbleGesture(private val touchSlopPx: Float) {
    data class Drag(val dx: Float, val dy: Float)

    enum class End { Tap, DragEnd, None }

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var dragging = false

    fun down(x: Float, y: Float) {
        downX = x
        downY = y
        tracking = true
        dragging = false
    }

    fun move(x: Float, y: Float): Drag? {
        if (!tracking) return null
        val dx = x - downX
        val dy = y - downY
        if (!dragging && hypot(dx, dy) <= touchSlopPx) return null
        dragging = true
        return Drag(dx, dy)
    }

    fun up(): End = finish(if (dragging) End.DragEnd else End.Tap)

    fun cancel(): End = finish(if (dragging) End.DragEnd else End.None)

    private fun finish(end: End): End {
        if (!tracking) return End.None
        tracking = false
        dragging = false
        return end
    }
}
