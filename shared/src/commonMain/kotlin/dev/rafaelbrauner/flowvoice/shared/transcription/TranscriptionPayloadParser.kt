package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.math.roundToLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// Lê a resposta da transcrição nos dois formatos (P146). O `json` traz só `text` e `usage`; o
// `verbose_json` traz também `words` e `segments`. Tudo o que tem tempo é opcional: o provedor pode
// aceitar o pedido e devolver a resposta sem nenhum tempo, e nesse caso o corte volta a ser por texto.
//
// Só `text` presente é transcrição (Y1). A OpenRouter pode mandar 200 com `{"error":{…}}` quando o
// provedor falha depois de a resposta começar; tomar isso, ou um corpo sem `text`, por texto vazio
// apagava a janela sem aviso e ensinava à memória da P153 que aquele nível era silêncio.
object TranscriptionPayloadParser {
    fun parse(raw: String): TranscriptionPayload {
        val body = try {
            payloadJson.decodeFromString(TranscriptionBody.serializer(), raw)
        } catch (_: IllegalArgumentException) {
            throw TranscriptionError.InvalidResponse("unparseable json")
        }
        body.error?.let { throw errorFrom(it) }
        val text = body.text ?: throw TranscriptionError.InvalidResponse("response without text")
        return TranscriptionPayload(
            text = text.trim(),
            words = body.words.orEmpty().mapNotNull { it.toTimedUnit() },
            segments = body.segments.orEmpty().mapNotNull { it.toTimedUnit() },
            costUsd = body.usage?.cost
        )
    }

    // O código do erro segue a tabela HTTP da OpenRouter: 5xx é falha do provedor, que se tenta de
    // novo, como o mesmo código vindo no status. Sem código, não há como saber se repetir adianta.
    private fun errorFrom(error: JsonElement): TranscriptionError {
        val fields = error as? JsonObject
        val message = ((fields?.get("message") ?: error) as? JsonPrimitive)?.contentOrNull
        val code = (fields?.get("code") as? JsonPrimitive)?.intOrNull
        if (code == null || code < 400) {
            val detail = TranscriptionErrorClassifier.sanitize(message)
            return TranscriptionError.InvalidResponse(listOfNotNull("error body", detail).joinToString(" "))
        }
        return TranscriptionErrorClassifier.fromHttpStatus(code, bodyMessage = message)
    }
}

// Os tempos vêm em segundos; a janela e o contexto são medidos em milissegundos.
private fun seconds(value: Double): Long = (value * 1_000.0).roundToLong()

@Serializable
private data class TranscriptionBody(
    val text: String? = null,
    val words: List<WordBody>? = null,
    val segments: List<SegmentBody>? = null,
    val usage: UsageBody? = null,
    val error: JsonElement? = null
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
