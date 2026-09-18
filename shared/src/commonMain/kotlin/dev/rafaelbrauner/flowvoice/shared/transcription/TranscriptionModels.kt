package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore

object TranscriptionModels {
    fun selected(preferences: PreferencesStore, config: OpenRouterConfig): String =
        preferences.read().transcriptionModel.trim()
            .ifEmpty { config.model }
}
