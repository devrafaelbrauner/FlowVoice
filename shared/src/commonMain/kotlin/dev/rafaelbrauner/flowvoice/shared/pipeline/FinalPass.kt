package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
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

// Passada final do motor no aparelho (duas passadas, 2026-09-28): o Nemotron digitou o rascunho ao vivo;
// ao parar, o áudio inteiro do ditado vai uma vez à nuvem, a transcrição volta com a pontuação falada
// aplicada, a formatação por LLM acerta pontuação, maiúsculas, acentos e concordância, e a guarda confere
// a formatação contra a transcrição da nuvem (não contra o rascunho, que tem outras palavras).
//
// - Transcrição falhou, demorou, veio vazia ou bem menor que o rascunho: fica o rascunho.
// - Só a formatação falhou, demorou ou foi recusada pela guarda: vale a transcrição da nuvem como veio.
//   Ela é a versão com menos erro de palavra (docs/medicao-duas-passadas.md) e já vem pontuada; a guarda
//   não tem contra o que conferi-la além dela mesma.
// Nada aqui escreve no campo nem registra texto no log comum: o texto só vai para `textLog` (P135).
internal class FinalPass(
    private val client: TranscriptionClient,
    private val proofreading: ProofreadingClient,
    private val log: (String, Map<String, String>) -> Unit,
    private val textLog: TranscriptionEventLog,
    private val timeSource: TimeSource,
    private val timeout: Duration = TIMEOUT
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
        formattingModel: String
    ): Result {
        val chunks = FinalPassAudio.chunks(windows)
        val audioMs = chunks.sumOf { it.durationMs }
        val base = mapOf("audioMs" to audioMs.toString(), "chunks" to chunks.size.toString())
        if (chunks.isEmpty()) return kept(REASON_NO_AUDIO, base)
        val started = timeSource.markNow()
        val transcripts = try {
            withTimeoutOrNull(timeout) {
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
                            client.transcribe(window, apiKey, transcriptionModel).text
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
        textLog.log("final_pass_transcript", mapOf("text" to transcript))

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
        log(
            "final_pass_done",
            timed + mapOf(
                "formatMs" to formatMs.toString(),
                "format" to formatOutcome,
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
        private const val FORMAT_OK = "ok"

        private val WORD = Regex("[\\p{L}\\p{N}]+")

        fun wordCount(text: String): Int = WORD.findAll(text).count()

        // Palavras em que a mistura ficou com a da transcrição e não a da formatação (recusas da guarda).
        private fun differingWords(merged: String, formatted: String): Int {
            val a = WORD.findAll(merged).map { it.value.lowercase() }.toList()
            val b = WORD.findAll(formatted).map { it.value.lowercase() }.toList()
            return a.indices.count { it < b.size && a[it] != b[it] }
        }
    }
}
