package dev.rafaelbrauner.flowvoice.shared.model

import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.shared.prefs.FormattingMode
import kotlin.math.roundToInt

// Raciocínio pedido ao modelo de chat. Sem ele parte dos modelos raciocina por padrão e a latência dobra;
// o Gemini 3.x Flash não aceita desligar e vai no mínimo (medição de 2026-09-28).
enum class Reasoning { Off, Minimal }

// Um modelo medido em docs/medicao-modelos-nuvem.md: a nota (0–100) é metade acerto ortográfico (1 − erro
// de palavra com acento) e metade pontuação (média das F1 de vírgula, fim de frase e "?"); a latência é a
// mediana, do Mac, de um ditado de 20 s do fim do áudio ao texto final (a combinação inteira: para a
// formatação, transcrição padrão + ela).
data class MeasuredModel(
    val id: String,
    val score: Double,
    val latencyMs: Long,
    val usdPerMinute: Double,
    val reasoning: Reasoning? = null
) {
    // "nota 95,1 · 3,8 s" — o resumo que Ajustes mostra ao lado do modelo.
    fun summary(): String = "nota ${decimal(score)} · ${decimal(latencyMs / 1000.0)} s"

    private fun decimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return "${tenths / 10},${tenths % 10}"
    }
}

// Como a passada final formata o texto (Ajustes → "Formatação"), nos dois motores.
sealed interface FormattingChoice {
    // Transcrição do áudio inteiro, depois formatação por este modelo de chat e a guarda.
    data class Llm(val model: String) : FormattingChoice

    // Só a transcrição do áudio inteiro, como veio.
    data object None : FormattingChoice

    // Um passo só: o áudio inteiro vai a um modelo de chat que ouve e devolve o texto já formatado.
    data class OneStep(val model: String) : FormattingChoice
}

// Uma linha do seletor "Formatação" de Ajustes.
data class FormattingOption(val choice: FormattingChoice, val label: String, val summary: String?)

// Os números são os de docs/medicao-modelos-nuvem.md (2026-09-28), gabarito corrigido: a nota da combinação e
// a mediana de um ditado de 20 s, do Mac. Ordem: a da nota.
object CloudModels {
    // O melhor passo único medido (nota 97,0); não é o padrão da passada final porque a transcrição +
    // formatação empata na nota (margem de 0,5 ponto), é mais rápida e tem a guarda contra a transcrição.
    const val DEFAULT_ONE_STEP_MODEL = "thinkingmachines/inkling"

    // Transcrição do áudio (ao vivo e na passada final): nota e tempo da transcrição sozinha.
    val transcription: List<MeasuredModel> = listOf(
        MeasuredModel("deepgram/nova-3", 96.1, 1940, 0.0043),
        MeasuredModel("openai/gpt-transcribe", 95.6, 2040, 0.0047),
        MeasuredModel("google/chirp-3", 95.3, 7590, 0.0166),
        MeasuredModel("openai/gpt-4o-transcribe", 95.3, 2070, 0.0035),
        MeasuredModel("microsoft/mai-transcribe-2", 95.2, 2030, 0.0017),
        MeasuredModel("x-ai/grok-stt-1.0", 95.0, 1910, 0.0017),
        MeasuredModel("qwen/qwen3-asr-flash-2026-02-10", 94.9, 2340, 0.0020),
        MeasuredModel("openai/gpt-4o-mini-transcribe", 94.7, 1650, 0.0018),
        MeasuredModel("microsoft/mai-transcribe-1.5", 94.3, 2070, 0.0062),
        MeasuredModel("mistralai/voxtral-small-24b-2507-stt", 94.0, 5140, 0.0030),
        MeasuredModel("google/gemini-3.5-transcribe", 93.8, 3390, 0.0030),
        MeasuredModel("qwen/qwen3-asr-1.7b", 92.5, 2560, 0.0005),
        MeasuredModel("mistralai/voxtral-mini-transcribe", 92.2, 1940, 0.0029),
        MeasuredModel("fish-audio/transcribe-1", 92.1, 1660, 0.0062),
        MeasuredModel("assemblyai/universal-3-5-pro", 91.5, 2660, 0.0037),
        MeasuredModel("mistralai/voxtral-mini-3b-2507", 91.0, 2260, 0.0010),
        MeasuredModel("fish-audio/transcribe-1-pro", 89.2, 1640, 0.0062),
        MeasuredModel("meta/muse-voice-transcribe-1.0", 88.8, 7300, 0.0030),
        MeasuredModel("openai/whisper-1", 87.9, 3040, 0.0062),
        MeasuredModel("nvidia/parakeet-tdt-0.6b-v3", 87.1, 1510, 0.0015),
        MeasuredModel("qwen/qwen3-asr-0.6b", 82.9, 1770, 0.0002),
        MeasuredModel("openai/whisper-large-v3-turbo", 82.6, 3580, 0.0002),
        MeasuredModel("openai/whisper-large-v3", 81.6, 7140, 0.0009),
        MeasuredModel("nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b", 44.9, 1990, 0.0002)
    )

    // Formatação depois da transcrição padrão (deepgram/nova-3): nota e tempo das duas etapas juntas.
    val formatting: List<MeasuredModel> = listOf(
        MeasuredModel("anthropic/claude-sonnet-5", 96.6, 5520, 0.0130),
        MeasuredModel("google/gemini-3.8-flash", 96.6, 4800, 0.0060, Reasoning.Minimal),
        MeasuredModel("google/gemini-3.1-flash-lite", 96.5, 3580, 0.0049, Reasoning.Off),
        MeasuredModel("openai/gpt-4.1", 96.5, 3620, 0.0080),
        MeasuredModel("openai/gpt-4.1-mini", 96.5, 3310, 0.0050),
        MeasuredModel("anthropic/claude-haiku-4.5", 96.4, 3500, 0.0069),
        MeasuredModel("google/gemini-3.5-flash-lite", 96.4, 3590, 0.0051),
        MeasuredModel("mistralai/mistral-small-3.2-24b-instruct", 96.2, 4020, 0.0045),
        MeasuredModel("deepseek/deepseek-v4.1-flash", 96.2, 15060, 0.0046, Reasoning.Off),
        MeasuredModel("openai/gpt-4o-mini", 96.2, 5140, 0.0046),
        MeasuredModel("meta-llama/llama-4-maverick", 96.1, 11120, 0.0046),
        MeasuredModel("qwen/qwen3.8-flash", 95.9, 5650, 0.0046, Reasoning.Off),
        MeasuredModel("openai/gpt-6-luna", 95.8, 3790, 0.0045, Reasoning.Off),
        MeasuredModel("mistralai/mistral-medium-3.1", 94.1, 3680, 0.0052)
    )

    // Passo único: só os que ficaram a até ~2,5 pontos do melhor e nunca responderam ao ditado (o
    // gpt-audio-mini respondeu "Claro, vou avisar assim que chegar." e fica fora).
    val oneStep: List<MeasuredModel> = listOf(
        MeasuredModel("thinkingmachines/inkling", 97.0, 4240, 0.0049),
        MeasuredModel("openai/gpt-audio", 95.5, 2650, 0.0228),
        MeasuredModel("google/gemini-3.5-flash", 94.8, 3060, 0.0073, Reasoning.Minimal),
        MeasuredModel("google/gemini-3.1-flash-lite", 94.5, 2630, 0.0012, Reasoning.Off),
        MeasuredModel("google/gemini-3-flash-preview", 94.5, 3250, 0.0024, Reasoning.Minimal),
        MeasuredModel("google/gemini-3.5-flash-lite", 94.4, 3010, 0.0011)
    )

    const val NO_FORMATTING_LABEL = "Sem formatação (só a transcrição)"
    private const val ONE_STEP_PREFIX = "Um passo só: "

    fun measured(id: String): MeasuredModel? =
        (formatting + oneStep + transcription).firstOrNull { it.id == id }

    fun reasoning(model: String): Reasoning? = measured(model)?.reasoning

    // Os medidos primeiro, na ordem da nota; depois o resto do catálogo da OpenRouter, na ordem dele.
    fun orderedTranscription(catalog: List<String>): List<String> {
        val measuredIds = transcription.map { it.id }.filter { it in catalog }
        return measuredIds + catalog.filter { it !in measuredIds }
    }

    fun transcriptionSummary(id: String): String? = transcription.firstOrNull { it.id == id }?.summary()

    fun formattingOptions(): List<FormattingOption> =
        formatting.map { FormattingOption(FormattingChoice.Llm(it.id), it.id, it.summary()) } +
            FormattingOption(FormattingChoice.None, NO_FORMATTING_LABEL, null) +
            oneStep.map { FormattingOption(FormattingChoice.OneStep(it.id), ONE_STEP_PREFIX + it.id, it.summary()) }

    fun label(choice: FormattingChoice): String = when (choice) {
        is FormattingChoice.Llm -> choice.model
        FormattingChoice.None -> NO_FORMATTING_LABEL
        is FormattingChoice.OneStep -> ONE_STEP_PREFIX + choice.model
    }

    fun formatting(preferences: AppPreferences): FormattingChoice = when (preferences.formattingMode) {
        FormattingMode.Llm -> FormattingChoice.Llm(
            preferences.proofreadingModel.trim().ifEmpty { AppPreferences.DEFAULT_PROOFREADING_MODEL }
        )
        FormattingMode.None -> FormattingChoice.None
        FormattingMode.OneStep -> FormattingChoice.OneStep(preferences.oneStepModel.trim().ifEmpty { DEFAULT_ONE_STEP_MODEL })
    }

    fun withFormatting(preferences: AppPreferences, choice: FormattingChoice): AppPreferences = when (choice) {
        is FormattingChoice.Llm -> preferences.copy(formattingMode = FormattingMode.Llm, proofreadingModel = choice.model)
        FormattingChoice.None -> preferences.copy(formattingMode = FormattingMode.None)
        is FormattingChoice.OneStep -> preferences.copy(formattingMode = FormattingMode.OneStep, oneStepModel = choice.model)
    }
}
