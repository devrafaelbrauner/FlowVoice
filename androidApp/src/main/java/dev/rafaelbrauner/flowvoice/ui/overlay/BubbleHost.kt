package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlinx.coroutines.flow.StateFlow

// Lado da bolha e direção em que a prévia cresce; durante o arraste só a bolha é desenhada.
data class BubbleLayout(
    val side: BubbleSide = BubblePosition.Default.side,
    val growsUp: Boolean = false,
    val dragging: Boolean = false
)

// Janela do overlay vista pela composição: a posição da bolha mora no serviço, que move a janela.
interface BubbleHost {
    val layout: StateFlow<BubbleLayout>

    fun onDrag(dx: Float, dy: Float)

    fun onDragEnd()

    fun onMove(move: BubbleMove)
}
