package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngineSelection
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences

enum class OnboardingStep { Accessibility, Microphone, Transcription }

data class OnboardingProgress(
    val accessibility: Boolean,
    val microphone: Boolean,
    val transcription: Boolean
) {
    fun isDone(step: OnboardingStep): Boolean = when (step) {
        OnboardingStep.Accessibility -> accessibility
        OnboardingStep.Microphone -> microphone
        OnboardingStep.Transcription -> transcription
    }

    val nextPending: OnboardingStep?
        get() = OnboardingStep.entries.firstOrNull { !isDone(it) }

    val allDone: Boolean
        get() = nextPending == null

    val primaryLabel: String
        get() = when (nextPending) {
            null -> CTA_START
            OnboardingStep.Accessibility -> "Ativar a acessibilidade"
            OnboardingStep.Microphone -> "Permitir o microfone"
            OnboardingStep.Transcription -> "Configurar a transcrição"
        }

    companion object {
        const val CTA_START = "Começar a ditar"
        const val CTA_SKIP = "Pular por enquanto"

        // O passo 3 vale tanto com a chave quanto com o modelo no aparelho em uso: qualquer um dos
        // dois já deixa ditar.
        fun of(
            accessibility: Boolean,
            microphone: Boolean,
            keyConfigured: Boolean,
            effectiveEngine: TranscriptionEngine
        ) = OnboardingProgress(
            accessibility = accessibility,
            microphone = microphone,
            transcription = TranscriptionEngineSelection.canDictate(effectiveEngine, keyConfigured)
        )
    }
}

// O onboarding termina sozinho quando os passos ficam ok, ou quando a pessoa pula: nos dois casos
// ele não volta a cada abertura, e o que faltar continua no Início e em Ajustes.
fun AppPreferences.afterOnboarding(progress: OnboardingProgress, skipped: Boolean): AppPreferences =
    if (!onboardingCompleted && (skipped || progress.allDone)) copy(onboardingCompleted = true) else this
