package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Lê a resposta da transcrição nos dois formatos (P146). O `json` traz só `text` e `usage`; o
// `verbose_json` traz também `words` e `segments`. Tudo o que tem tempo é opcional: o provedor pode
// aceitar o pedido e devolver a resposta sem nenhum tempo, e nesse caso o corte volta a ser por texto.
object TranscriptionPayloadParser {
    fun parse(raw: String): TranscriptionPayload? =
        try {
            val body = payloadJson.decodeFromString(TranscriptionBody.serializer(), raw)
            TranscriptionPayload(
                text = body.text?.trim().orEmpty(),
                words = body.words.orEmpty().mapNotNull { it.toTimedUnit() },
                segments = body.segments.orEmpty().mapNotNull { it.toTimedUnit() },
                costUsd = body.usage?.cost
            )
        } catch (_: Throwable) {
            null
        }
}

// Os tempos vêm em segundos; a janela e o contexto são medidos em milissegundos.
private fun seconds(value: Double): Long = (value * 1_000.0).roundToLong()

@Serializable
private data class TranscriptionBody(
    val text: String? = null,
    val words: List<WordBody>? = null,
    val segments: List<SegmentBody>? = null,
    val usage: UsageBody? = null
)

// `word` é o nome do campo na OpenAI; `text` aparece em provedores compatíveis.
@Serializable
private data class WordBody(
    val word: String? = null,
    val text: String? = null,
    val start: Double? = null,
    val end: Double? = null
) {
    fun toTimedUnit(): TimedUnit? {
        val content = word ?: text ?: return null
        val startSec = start ?: return null
        val endSec = end ?: return null
        return TimedUnit(content.trim(), seconds(startSec), seconds(endSec))
    }
}

@Serializable
private data class SegmentBody(
    val text: String? = null,
    val start: Double? = null,
    val end: Double? = null
) {
    fun toTimedUnit(): TimedUnit? {
        val content = text ?: return null
        val startSec = start ?: return null
        val endSec = end ?: return null
        return TimedUnit(content.trim(), seconds(startSec), seconds(endSec))
    }
}

@Serializable
private data class UsageBody(val cost: Double? = null)

private val payloadJson = Json { ignoreUnknownKeys = true }
