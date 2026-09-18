package dev.rafaelbrauner.flowvoice.shared.prefs

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val proofreadingEnabled: Boolean = false,
    val proofreadingModel: String = DEFAULT_PROOFREADING_MODEL,
    val googleWebClientId: String = "",
    val syncEndpoint: String = "",
    val loginCompleted: Boolean = false,
    val onboardingCompleted: Boolean = false,
    // Desligado (padrão), a bolha digita cada trecho direto no campo (P139); ligado, mantém a barra com Inserir e a revisão por IA.
    val reviewBeforeInsert: Boolean = false,
    // Modelo de transcrição escolhido em Ajustes (lista da OpenRouter). Vazio mantém o padrão do benchmark F05.
    val transcriptionModel: String = ""
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
