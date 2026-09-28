package dev.rafaelbrauner.flowvoice.shared.prefs

import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
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
    val transcriptionModel: String = "",
    // "Motor de transcrição" (0.7.0): nuvem por padrão; o local só vale com o modelo no aparelho.
    val transcriptionEngine: TranscriptionEngine = TranscriptionEngine.Cloud,
    // "Formatação" em Ajustes (`CloudModels.formatting`): como a passada final formata, nos dois motores.
    // `proofreadingModel` é o modelo de `Llm`; `oneStepModel` o de `OneStep` (vazio = o padrão medido).
    val formattingMode: FormattingMode = FormattingMode.Llm,
    val oneStepModel: String = ""
) {
    companion object {
        // Formatação da passada final: o vencedor da medição de docs/medicao-modelos-nuvem.md (2026-09-28) —
        // empatado na nota com os melhores sobre o deepgram/nova-3 e o mais rápido deles (3,3 s num ditado de 20 s).
        const val DEFAULT_PROOFREADING_MODEL = "openai/gpt-4.1-mini"
    }
}

@Serializable
enum class FormattingMode { Llm, None, OneStep }

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
