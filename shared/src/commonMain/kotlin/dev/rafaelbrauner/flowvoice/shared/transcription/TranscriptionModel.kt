package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore

class TranscriptionModel(
    private val preferences: PreferencesStore
) {
    fun current(): String =
        preferences.read().transcriptionModel.trim()
            .ifEmpty { OpenRouterConfig.DEFAULT_MODEL }

    fun save(model: String) {
        val id = model.trim()
        require(id.isNotBlank()) { "model must not be blank" }
        val current = preferences.read()
        if (current.transcriptionModel != id) {
            preferences.write(current.copy(transcriptionModel = id))
        }
    }

    fun clear() {
        val current = preferences.read()
        if (current.transcriptionModel.isNotEmpty()) {
            preferences.write(current.copy(transcriptionModel = ""))
        }
    }
}
