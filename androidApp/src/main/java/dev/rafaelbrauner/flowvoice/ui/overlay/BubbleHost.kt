package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline

// Ações do cartão da prévia, que mora numa janela própria (P139) mas mexe no estado da composição da bolha.
class PreviewActions(
    val onCancel: () -> Unit,
    val onInsertHere: () -> Unit,
    val onDismiss: () -> Unit
)

// O que a janela da prévia desenha: o estado e a altura máxima que o serviço escolheu longe do cursor.
data class PreviewCardUi(
    val state: DirectPreviewState = DirectPreviewState.Hidden,
    val maxHeightPx: Int = 0,
    val compact: Boolean = false,
    val actions: PreviewActions? = null
)

// Janelas do overlay vistas pela composição da bolha: o serviço move a bolha e abre, posiciona e fecha a prévia.
interface BubbleHost {
    fun onDrag(dx: Float, dy: Float)

    fun onDragEnd()

    fun onMove(move: BubbleMove)

    fun updatePreview(state: DirectPreviewState, actions: PreviewActions)
}

internal fun previewActionsFor(pipeline: DictationPipeline, onDismiss: () -> Unit) = PreviewActions(
    onCancel = { pipeline.requestCancel() },
    onInsertHere = { pipeline.requestInsertPending() },
    onDismiss = onDismiss
)
