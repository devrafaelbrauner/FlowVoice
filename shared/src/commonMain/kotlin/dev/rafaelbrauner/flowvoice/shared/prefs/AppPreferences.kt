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
    // Padrão: sem formatação — a transcrição do áudio inteiro (`OpenRouterConfig.DEFAULT_MODEL`) já vem
    // pontuada, sem erro de sentido e em ~1,9 s no S26; o passo único (inkling), de nota maior, deu
    // 7,4 s e erro de servidor no aparelho (escolha do usuário, 2026-09-28).
    val formattingMode: FormattingMode = FormattingMode.None,
    val oneStepModel: String = ""
) {
    companion object {
        // Formatação por LLM, quando escolhida: a vencedora da transcrição + formatação em
        // docs/medicao-modelos-nuvem.md (2026-09-28) — empatada na nota e a mais rápida sobre o gpt-transcribe.
        const val DEFAULT_PROOFREADING_MODEL = "openai/gpt-4.1"
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
