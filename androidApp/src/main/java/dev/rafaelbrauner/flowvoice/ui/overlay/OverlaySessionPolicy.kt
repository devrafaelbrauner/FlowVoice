package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget

object OverlaySessionPolicy {
    fun ownedAfter(owned: Boolean, status: DictationPipelineStatus): Boolean = owned && status.isBusy

    /**
     * Ocultar o botão cancela só o ditado que a bolha começou (P40). Adotar uma sessão ocupada dá os
     * controles na barra, mas não dá à bolha o direito de cancelar ao ser destruída (P103).
     */
    fun ownsSession(ownership: OverlayOwnership): Boolean = ownership.owned && ownership.startedHere

    fun cancelOnDestroy(ownsSession: Boolean, target: DictationTarget, status: DictationPipelineStatus): Boolean =
        ownsSession && target == DictationTarget.ActiveField && status.isBusy

    /**
     * Ocultar o botão não pode levar junto a única coisa que mostra o ditado em andamento (P159): com a
     * barra ou a prévia na tela o overlay continua vivo e só a bolha some. Quando não sobra mais nada —
     * o modo volta a ser "só bolha" — não há o que mostrar e o serviço encerra.
     */
    fun shouldStopWithHiddenBubble(bubbleHidden: Boolean, mode: OverlayMode): Boolean =
        bubbleHidden && mode == OverlayMode.Bubble
}
