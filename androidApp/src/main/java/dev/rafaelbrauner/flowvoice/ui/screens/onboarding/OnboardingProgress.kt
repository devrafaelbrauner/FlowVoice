package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

data class OnboardingProgress(
    val accessibility: Boolean,
    val microphone: Boolean,
    val key: Boolean
) {
    val allDone: Boolean
        get() = accessibility && microphone && key

    val ctaLabel: String
        get() = if (allDone) CTA_START else CTA_SKIP

    companion object {
        const val CTA_START = "Começar a ditar"
        const val CTA_SKIP = "Pular por enquanto"
    }
}
