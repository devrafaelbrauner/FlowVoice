package dev.rafaelbrauner.flowvoice.ui.overlay

object BubbleVisibility {
    /**
     * Com o FlowVoice na tela, a bolha ociosa cobria o microfone do Início e valores dos Ajustes e do
     * Diagnóstico (R5d): ela some sem derrubar o serviço e volta quando o app sai da frente. Com a
     * prévia de um ditado direto na tela a bolha é o "parar" dele (P159), então fica mesmo dentro do
     * app; na barra ela já não é desenhada. "Ocultar botão" continua valendo sempre.
     */
    fun showBubble(hiddenByUser: Boolean, appInForeground: Boolean, mode: OverlayMode): Boolean = when {
        hiddenByUser -> false
        mode == OverlayMode.Bar -> false
        mode == OverlayMode.Preview -> true
        else -> !appInForeground
    }
}
