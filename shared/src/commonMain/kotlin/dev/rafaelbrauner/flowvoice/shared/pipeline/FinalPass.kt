package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.model.FormattingChoice
import dev.rafaelbrauner.flowvoice.shared.proofreading.AudioChatClient
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingGuard
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingMerge
import dev.rafaelbrauner.flowvoice.shared.text.SpokenPunctuation
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionEventLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

// Passada final (duas passadas, 2026-09-28; nos dois motores desde a medição de modelos da nuvem): o
// rascunho — do Nemotron ou das janelas da nuvem — já foi digitado ao vivo; ao parar, o áudio inteiro do
// ditado vai uma vez à nuvem e o texto final troca o rascunho. Como formatar vem de Ajustes
// (`FormattingChoice`, docs/medicao-modelos-nuvem.md):
// - `Llm`: transcrição do áudio inteiro com a pontuação falada aplicada, formatação por LLM (pontuação,
//   maiúsculas, acentos, concordância) e a guarda, que confere a formatação contra a transcrição da nuvem
//   (não contra o rascunho, que tem outras palavras).
// - `None`: só a transcrição, como veio.
// - `OneStep`: um modelo de chat ouve o áudio e devolve o texto já formatado. Não há transcrição contra a
//   qual conferir palavra por palavra: além da trava de cobertura, ao menos ~1/3 das palavras do rascunho
//   têm de estar no texto final. Um modelo que responde ao que foi dito em vez de transcrever ("Me avisa
//   quando chegar" → "Claro, vou avisar assim que chegar.", gpt-audio-mini na medição) fica abaixo disso.
//
// - Transcrição (ou passo único) falhou, demorou, veio vazia ou bem menor que o rascunho: fica o rascunho.
// - Só a formatação falhou, demorou ou foi recusada pela guarda: vale a transcrição da nuvem como veio.
//   Ela é a versão com menos erro de palavra (docs/medicao-duas-passadas.md) e já vem pontuada; a guarda
//   não tem contra o que conferi-la além dela mesma.
// Nada aqui escreve no campo nem registra texto no log comum: o texto só vai para `textLog` (P135).
internal class FinalPass(
    private val client: TranscriptionClient,
    private val proofreading: ProofreadingClient,
    private val audioChat: AudioChatClient,
    private val log: (String, Map<String, String>) -> Unit,
    private val textLog: TranscriptionEventLog,
    private val timeSource: TimeSource,
    private val timeout: Duration = TIMEOUT,
    private val oneStepTimeout: Duration = ONE_STEP_TIMEOUT
) {
    sealed interface Result {
        data class Final(val text: String) : Result

        data class Kept(val reason: String) : Result
    }

    suspend fun run(
        draft: String,
        windows: List<DictationWindow>,
        apiKey: String,
        transcriptionModel: String,
        formatting: FormattingChoice
    ): Result {
        val chunks = FinalPassAudio.chunks(windows)
        val audioMs = chunks.sumOf { it.durationMs }
        val base = mapOf(
            "audioMs" to audioMs.toString(),
            "chunks" to chunks.size.toString(),
            "mode" to modeOf(formatting),
            "model" to ((formatting as? FormattingChoice.OneStep)?.model ?: transcriptionModel)
        )
        if (chunks.isEmpty()) return kept(REASON_NO_AUDIO, base)
        val started = timeSource.markNow()
        val limit = if (formatting is FormattingChoice.OneStep) oneStepTimeout else timeout
        val transcripts = try {
            withTimeoutOrNull(limit) {
                coroutineScope {
                    chunks.mapIndexed { index, chunk ->
                        async {
                            val window = DictationWindow(
                                index = index,
                                pcm = chunk.pcm,
                                format = chunk.format,
                                startedAtMs = 0L,
                                finishedAtMs = chunk.durationMs
                            )
                            if (formatting is FormattingChoice.OneStep) {
                                audioChat.transcribeFormatted(window, apiKey, formatting.model)
                            } else {
                                client.transcribe(window, apiKey, transcriptionModel).text
                            }
                        }
                    }.awaitAll()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val kind = (error as? TranscriptionError)?.kind ?: "desconhecido"
            return kept(REASON_TRANSCRIPTION_ERROR, base + ("kind" to kind))
        } ?: return kept(REASON_TIMEOUT, base + ("stage" to "transcricao"))
        val transcribeMs = started.elapsedNow().inWholeMilliseconds
        val timed = base + ("transcribeMs" to transcribeMs.toString())
        val transcript = SpokenPunctuation.apply(transcripts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" "))
        if (transcript.isBlank()) return kept(REASON_EMPTY, timed)
        // A nuvem devolveu bem menos palavras que o rascunho: pedaço perdido ou resposta cortada. Trocar
        // perderia texto que o rascunho tem.
        if (wordCount(transcript) * COVERAGE_DENOMINATOR < wordCount(draft) * COVERAGE_NUMERATOR) {
            return kept(REASON_COVERAGE, timed)
        }
        if (formatting is FormattingChoice.OneStep && draftOverlap(draft, transcript) < ONE_STEP_MIN_OVERLAP) {
            return kept(REASON_DIVERGED, timed)
        }
        textLog.log("final_pass_transcript", mapOf("text" to transcript))
        val formattingModel = (formatting as? FormattingChoice.Llm)?.model
            ?: return done(transcript, draft, timed, formatMs = 0L, outcome = FORMAT_SKIPPED, keptWords = 0)

        val formatStarted = timeSource.markNow()
        val remaining = timeout - started.elapsedNow()
        var formatOutcome = FORMAT_OK
        val formatted = if (remaining <= Duration.ZERO) {
            formatOutcome = REASON_TIMEOUT
            null
        } else {
            try {
                withTimeoutOrNull(remaining) { proofreading.proofread(transcript, apiKey, formattingModel) }
                    .also { if (it == null) formatOutcome = REASON_TIMEOUT }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                formatOutcome = REASON_FORMAT_ERROR
                null
            }
        }
        val formatMs = formatStarted.elapsedNow().inWholeMilliseconds
        var keptWords = 0
        val final = if (formatted == null || formatted.isBlank()) {
            if (formatted != null) formatOutcome = REASON_EMPTY
            transcript
        } else {
            textLog.log("final_pass_formatted", mapOf("text" to formatted))
            val merged = ProofreadingMerge.merge(transcript, formatted) ?: formatted
            if (ProofreadingGuard.accepts(transcript, merged)) {
                keptWords = differingWords(merged, formatted)
                merged
            } else {
                formatOutcome = REASON_GUARD
                transcript
            }
        }
        return done(final, draft, timed + ("formattingModel" to formattingModel), formatMs, formatOutcome, keptWords)
    }

    private fun done(
        final: String,
        draft: String,
        metadata: Map<String, String>,
        formatMs: Long,
        outcome: String,
        keptWords: Int
    ): Result {
        log(
            "final_pass_done",
            metadata + mapOf(
                "formatMs" to formatMs.toString(),
                "format" to outcome,
                "keptWords" to keptWords.toString(),
                "draftChars" to draft.length.toString(),
                "chars" to final.length.toString()
            )
        )
        return Result.Final(final)
    }

    private fun kept(reason: String, metadata: Map<String, String>): Result {
        log("final_pass_kept_draft", metadata + ("reason" to reason))
        return Result.Kept(reason)
    }

    companion object {
        // Teto da passada final inteira, do toque de parar até o texto final (transcrição e formatação). O
        // rascunho já está no campo; passado o teto ele fica.
        val TIMEOUT: Duration = 8_000.milliseconds
        // O passo único é uma etapa só, mas o melhor (inkling) chegou a 7,8 s (p95) nos longos no Mac: 12 s.
        val ONE_STEP_TIMEOUT: Duration = 12_000.milliseconds

        // A transcrição da nuvem precisa ter ao menos 60 % das palavras do rascunho.
        private const val COVERAGE_NUMERATOR = 3
        private const val COVERAGE_DENOMINATOR = 5

        const val REASON_NO_AUDIO = "sem_audio"
        const val REASON_TRANSCRIPTION_ERROR = "erro_transcricao"
        const val REASON_TIMEOUT = "demorou"
        const val REASON_EMPTY = "vazio"
        const val REASON_COVERAGE = "cobertura"
        const val REASON_FORMAT_ERROR = "erro_formatacao"
        const val REASON_GUARD = "guarda"
        const val REASON_DIVERGED = "diverge"
        private const val FORMAT_OK = "ok"
        // Sem formatação por LLM: "sem formatação" em Ajustes, ou o passo único, que já devolve o texto formatado.
        private const val FORMAT_SKIPPED = "nenhuma"

        const val MODE_TWO_STEP = "dois_passos"
        const val MODE_TRANSCRIPT_ONLY = "so_transcricao"
        const val MODE_ONE_STEP = "um_passo"

        fun modeOf(formatting: FormattingChoice): String = when (formatting) {
            is FormattingChoice.Llm -> MODE_TWO_STEP
            FormattingChoice.None -> MODE_TRANSCRIPT_ONLY
            is FormattingChoice.OneStep -> MODE_ONE_STEP
        }

        private val WORD = Regex("[\\p{L}\\p{N}]+")

        fun wordCount(text: String): Int = WORD.findAll(text).count()

        // Na medição, contra o rascunho do Nemotron (o pior, com até ~40 % de erro nas frases de trabalho),
        // o texto de um passo dos modelos que transcrevem teve ao menos 44 % das palavras do rascunho; a
        // resposta do gpt-audio-mini, 17 %.
        const val ONE_STEP_MIN_OVERLAP = 0.35

        // Fração das palavras do rascunho que aparecem no texto final, sem acento nem caixa e com número por
        // extenso igual ao algarismo ("sete" = "7").
        fun draftOverlap(draft: String, final: String): Double {
            val draftWords = comparable(draft)
            if (draftWords.isEmpty()) return 1.0
            val finalWords = comparable(final).toSet()
            return draftWords.count { it in finalWords }.toDouble() / draftWords.size
        }

        private fun comparable(text: String): List<String> =
            WORD.findAll(stripAccents(text.lowercase())).map { NUMBER_WORDS[it.value] ?: it.value }.toList()

        private fun stripAccents(text: String): String = buildString(text.length) {
            text.forEach { c -> append(ACCENTS[c] ?: c) }
        }

        private val ACCENTS: Map<Char, Char> = buildMap {
            "áàâãä".forEach { put(it, 'a') }
            "éèêë".forEach { put(it, 'e') }
            "íìîï".forEach { put(it, 'i') }
            "óòôõö".forEach { put(it, 'o') }
            "úùûü".forEach { put(it, 'u') }
            put('ç', 'c')
        }

        private val NUMBER_WORDS: Map<String, String> = listOf(
            "zero", "um", "dois", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze", "doze",
            "treze", "quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove", "vinte"
        ).mapIndexed { value, word -> word to value.toString() }.toMap() +
            mapOf("uma" to "1", "duas" to "2", "catorze" to "14")

        // Palavras em que a mistura ficou com a da transcrição e não a da formatação (recusas da guarda).
        private fun differingWords(merged: String, formatted: String): Int {
            val a = WORD.findAll(merged).map { it.value.lowercase() }.toList()
            val b = WORD.findAll(formatted).map { it.value.lowercase() }.toList()
            return a.indices.count { it < b.size && a[it] != b[it] }
        }
    }
}
