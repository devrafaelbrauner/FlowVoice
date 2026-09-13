package dev.rafaelbrauner.flowvoice.shared.prefs

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val proofreadingEnabled: Boolean = false,
    val proofreadingModel: String = DEFAULT_PROOFREADING_MODEL,
    val googleWebClientId: String = "",
    val syncEndpoint: String = ""
) {
    companion object {
        const val DEFAULT_PROOFREADING_MODEL = "openai/gpt-4o-mini"
    }
}

interface PreferencesStore {
    fun read(): AppPreferences
    fun write(value: AppPreferences)
}

class InMemoryPreferencesStore(
    initial: AppPreferences = AppPreferences()
) : PreferencesStore {
    private var value = initial
    override fun read(): AppPreferences = value
    override fun write(value: AppPreferences) {
        this.value = value
    }
}
