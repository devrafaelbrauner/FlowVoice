package dev.rafaelbrauner.flowvoice.shared.model

import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.shared.prefs.FormattingMode
import kotlin.math.roundToInt

// Raciocínio pedido ao modelo de chat. Sem ele parte dos modelos raciocina por padrão e a latência dobra;
// o Gemini 3.x Flash não aceita desligar e vai no mínimo (medição de 2026-09-28).
enum class Reasoning { Off, Minimal }

// Um modelo medido em docs/medicao-modelos-nuvem.md. `meaningErrors` são os erros que mudam o sentido no
// corpus (prefixo de negação, negação, número, termo clínico trocado); qualquer um desclassifica o modelo como
// padrão. A nota (0–100) é metade acerto ortográfico (1 − erro de palavra com acento, número por extenso =
// algarismo, abreviação = forma longa) e metade pontuação (média das F1 de vírgula, fim de frase e "?"); a
// latência é a mediana, do Mac, de um ditado de 20 s do fim do áudio ao texto final (a combinação inteira: para
// a formatação, `openai/gpt-transcribe` + ela).
data class MeasuredModel(
    val id: String,
    val score: Double,
    val latencyMs: Long,
    val usdPerMinute: Double,
    val meaningErrors: Int,
    val reasoning: Reasoning? = null
) {
    // "0 erros de sentido · nota 97,7 · 4,2 s" — o resumo que Ajustes mostra ao lado do modelo.
    fun summary(): String {
        val errors = if (meaningErrors == 1) "1 erro de sentido" else "$meaningErrors erros de sentido"
        return "$errors · nota ${decimal(score)} · ${decimal(latencyMs / 1000.0)} s"
    }

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

// Os números são os de docs/medicao-modelos-nuvem.md (2026-09-28, gabarito corrigido). Ordem: a da escolha
// (menos erros que mudam sentido, depois a nota).
object CloudModels {
    // Passo único e padrão da passada final: o único sem erro que muda sentido com a melhor nota de todas
    // (97,7). Os longos chegam a 7,8 s (p95), por isso o teto maior (`FinalPass.ONE_STEP_TIMEOUT`).
    const val DEFAULT_ONE_STEP_MODEL = "thinkingmachines/inkling"

    // Transcrição do áudio (ao vivo; na passada final só com formatação por LLM ou sem formatação).
    val transcription: List<MeasuredModel> = listOf(
        MeasuredModel("google/chirp-3", 96.3, 7590, 0.0166, 0),
        MeasuredModel("openai/gpt-4o-mini-transcribe", 95.8, 1650, 0.0018, 0),
        MeasuredModel("google/gemini-3.5-transcribe", 94.8, 3390, 0.0030, 0),
        MeasuredModel("openai/gpt-transcribe", 96.5, 2040, 0.0047, 1),
        MeasuredModel("microsoft/mai-transcribe-2", 95.9, 2030, 0.0017, 1),
        MeasuredModel("qwen/qwen3-asr-flash-2026-02-10", 95.8, 2340, 0.0020, 2),
        MeasuredModel("openai/gpt-4o-transcribe", 96.4, 2070, 0.0035, 4),
        MeasuredModel("deepgram/nova-3", 96.1, 1940, 0.0043, 4),
        MeasuredModel("mistralai/voxtral-small-24b-2507-stt", 95.0, 5140, 0.0030, 4),
        MeasuredModel("assemblyai/universal-3-5-pro", 92.2, 2660, 0.0037, 5),
        MeasuredModel("microsoft/mai-transcribe-1.5", 95.2, 2070, 0.0062, 7),
        MeasuredModel("mistralai/voxtral-mini-3b-2507", 92.0, 2260, 0.0010, 7),
        MeasuredModel("x-ai/grok-stt-1.0", 95.9, 1910, 0.0017, 8),
        MeasuredModel("mistralai/voxtral-mini-transcribe", 93.0, 1940, 0.0029, 8),
        MeasuredModel("meta/muse-voice-transcribe-1.0", 88.8, 7300, 0.0030, 9),
        MeasuredModel("openai/whisper-large-v3-turbo", 83.6, 3580, 0.0002, 9),
        MeasuredModel("qwen/qwen3-asr-1.7b", 92.7, 2560, 0.0005, 13),
        MeasuredModel("fish-audio/transcribe-1", 91.8, 1660, 0.0062, 13),
        MeasuredModel("openai/whisper-1", 88.7, 3040, 0.0062, 13),
        MeasuredModel("openai/whisper-large-v3", 82.4, 7140, 0.0009, 15),
        MeasuredModel("nvidia/parakeet-tdt-0.6b-v3", 87.9, 1510, 0.0015, 19),
        MeasuredModel("qwen/qwen3-asr-0.6b", 82.9, 1770, 0.0002, 23),
        MeasuredModel("fish-audio/transcribe-1-pro", 90.1, 1640, 0.0062, 29),
        MeasuredModel("nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b", 44.9, 1990, 0.0002, 31)
    )

    // Formatação depois do `openai/gpt-transcribe` (a transcrição das combinações medidas com menos erro de
    // sentido): nota, erros e tempo das duas etapas juntas.
    val formatting: List<MeasuredModel> = listOf(
        MeasuredModel("google/gemini-3.5-flash-lite", 96.5, 4210, 0.0055, 1),
        MeasuredModel("anthropic/claude-haiku-4.5", 96.2, 4600, 0.0073, 1),
        MeasuredModel("anthropic/claude-sonnet-5", 96.2, 5120, 0.0113, 1),
        MeasuredModel("deepseek/deepseek-v4.1-flash", 96.2, 4640, 0.0050, 1, Reasoning.Off),
        MeasuredModel("google/gemini-3.1-flash-lite", 96.2, 3980, 0.0052, 1, Reasoning.Off),
        MeasuredModel("google/gemini-3.8-flash", 96.2, 4210, 0.0064, 1, Reasoning.Minimal),
        MeasuredModel("qwen/qwen3.8-flash", 96.2, 4700, 0.0049, 1, Reasoning.Off),
        MeasuredModel("openai/gpt-4.1", 96.1, 3780, 0.0084, 1),
        MeasuredModel("openai/gpt-4.1-mini", 96.0, 5240, 0.0054, 1),
        MeasuredModel("mistralai/mistral-small-3.2-24b-instruct", 96.0, 5000, 0.0048, 1),
        MeasuredModel("meta-llama/llama-4-maverick", 95.7, 9540, 0.0050, 1),
        MeasuredModel("openai/gpt-4o-mini", 95.6, 4560, 0.0049, 1),
        MeasuredModel("openai/gpt-6-luna", 95.5, 4230, 0.0049, 1, Reasoning.Off),
        MeasuredModel("mistralai/mistral-medium-3.1", 95.8, 3950, 0.0055, 2)
    )

    // Passo único: só os sem erro que muda sentido e sem resposta ao ditado (o gpt-audio-mini respondeu "Claro,
    // vou avisar assim que chegar."; o gpt-audio trocou "cefaleia" por "se falei").
    val oneStep: List<MeasuredModel> = listOf(
        MeasuredModel("thinkingmachines/inkling", 97.7, 4240, 0.0049, 0),
        MeasuredModel("google/gemini-3.5-flash", 95.7, 3060, 0.0073, 0, Reasoning.Minimal),
        MeasuredModel("google/gemini-3-flash-preview", 95.4, 3250, 0.0024, 0, Reasoning.Minimal),
        MeasuredModel("google/gemini-3.1-flash-lite", 95.4, 2630, 0.0012, 0, Reasoning.Off)
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
