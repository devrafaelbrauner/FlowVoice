package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.encodeBase64
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenRouterTranscriptionClient(
    private val http: HttpClient,
    private val config: OpenRouterConfig,
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp
) : TranscriptionClient {
    @Volatile
    private var currentJob: Job? = null

    // Modelos que não devolvem tempos (P146), porque recusaram `verbose_json` ou responderam sem
    // `words`/`segments`. Guardado por modelo, não por sessão: quem não devolve tempo hoje não passa a
    // devolver no meio do ditado, e assim a recusa não se repete a cada janela. Escrita por cópia —
    // as janelas são transcritas uma de cada vez, mas o cliente é único.
    @Volatile
    private var withoutTimestamps: Set<String> = emptySet()

    override suspend fun transcribe(
        window: DictationWindow,
        apiKey: String,
        model: String?
    ): TranscriptionResult {
        currentJob = coroutineContext[Job]
        val usedModel = model?.takeIf { it.isNotBlank() } ?: config.model
        eventLog.log(
            "transcription_request",
            mapOf(
                "window" to window.index.toString(),
                "durationMs" to window.durationMs.toString(),
                // Contexto sobreposto enviado junto (P143): mede o áudio a mais por pedido.
                "contextMs" to window.contextDurationMs.toString(),
                "model" to usedModel
            )
        )
        val audio = OpenRouterInputAudio(
            data = WavEncoder.encode(window.transmittedPcm, window.format).encodeBase64(),
            format = "wav"
        )
        // Só há repetição para cortar quando a janela leva contexto: a janela 0 vai sozinha e não paga
        // uma resposta maior.
        var verbose = window.contextDurationMs > 0L && usedModel !in withoutTimestamps
        var httpStatus: Int? = null
        return try {
            var payload: TranscriptionPayload? = null
            while (payload == null) {
                val response = post(audio, usedModel, apiKey, verbose)
                val status = response.status.value
                val raw = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    // Provedor que recusa o formato: a janela é refeita em `json` na hora, para o
                    // ditado não parar por causa de um campo a mais no pedido.
                    if (verbose && status in FORMAT_REFUSED) {
                        dropTimestamps(window, usedModel, "http_$status", status)
                        verbose = false
                        continue
                    }
                    httpStatus = status
                    throw TranscriptionErrorClassifier.fromHttpStatus(
                        status,
                        response.headers[HttpHeaders.RetryAfter],
                        extractErrorMessage(raw)
                    )
                }
                payload = TranscriptionPayloadParser.parse(raw)
                    ?: throw TranscriptionError.InvalidResponse("unparseable json")
                // Aceitou o formato e não mandou tempo: refazer não adiantaria e o texto já veio.
                if (verbose && !payload.hasTimes && payload.text.isNotEmpty()) {
                    dropTimestamps(window, usedModel, "sem_tempos", null)
                }
            }
            val trimmed = ContextTrim.trim(payload, window.contextDurationMs)
            if (trimmed.strategy != ContextTrim.Strategy.Text) {
                eventLog.log(
                    "transcription_context_trimmed",
                    mapOf(
                        "window" to window.index.toString(),
                        "strategy" to trimmed.strategy.name.lowercase(),
                        "contextMs" to window.contextDurationMs.toString(),
                        "droppedMs" to trimmed.droppedMs.toString(),
                        "droppedWords" to trimmed.droppedWords.toString(),
                        "droppedSegments" to trimmed.droppedSegments.toString()
                    )
                )
            }
            val text = trimmed.text.trim()
            val result = TranscriptionResult(
                text = text,
                model = usedModel,
                durationMs = window.durationMs,
                costUsd = payload.costUsd
            )
            eventLog.log(
                "transcription_success",
                mapOf(
                    "window" to window.index.toString(),
                    "durationMs" to window.durationMs.toString(),
                    "model" to usedModel,
                    "chars" to text.length.toString()
                )
            )
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: TranscriptionError) {
            logError(window, usedModel, error, httpStatus)
            throw error
        } catch (error: Throwable) {
            val classified = TranscriptionErrorClassifier.fromThrowable(error)
            logError(window, usedModel, classified, httpStatus)
            throw classified
        }
    }

    private suspend fun post(
        audio: OpenRouterInputAudio,
        model: String,
        apiKey: String,
        verbose: Boolean
    ): HttpResponse {
        val requestBody = OpenRouterSttRequest(
            model = model,
            language = config.language,
            temperature = config.temperature,
            inputAudio = audio,
            responseFormat = if (verbose) VERBOSE_JSON else null,
            timestampGranularities = if (verbose) WORD_GRANULARITY else null
        )
        return http.post("${config.baseUrl}${config.transcriptionsPath}") {
            timeout {
                connectTimeoutMillis = config.connectTimeoutMs
                requestTimeoutMillis = config.requestTimeoutMs
                socketTimeoutMillis = config.requestTimeoutMs
            }
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("X-Title", "FlowVoice")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }
    }

    private fun dropTimestamps(window: DictationWindow, model: String, reason: String, status: Int?) {
        if (model in withoutTimestamps) return
        withoutTimestamps = withoutTimestamps + model
        eventLog.log(
            "transcription_verbose_unsupported",
            buildMap {
                put("window", window.index.toString())
                put("model", model)
                put("reason", reason)
                if (status != null) put("status", status.toString())
            }
        )
    }

    private fun logError(window: DictationWindow, model: String, error: TranscriptionError, httpStatus: Int?) {
        eventLog.log(
            "transcription_error",
            buildMap {
                put("window", window.index.toString())
                put("durationMs", window.durationMs.toString())
                put("model", model)
                put("kind", error.kind)
                if (httpStatus != null) put("status", httpStatus.toString())
            }
        )
    }

    override fun cancel() {
        currentJob?.cancel()
    }

    private fun extractErrorMessage(raw: String): String? =
        try {
            responseJson.decodeFromString(OpenRouterErrorEnvelope.serializer(), raw).error?.message
        } catch (_: Throwable) {
            null
        }
}

@Serializable
internal data class OpenRouterSttRequest(
    val model: String,
    val language: String? = null,
    val temperature: Double? = null,
    @SerialName("response_format") val responseFormat: String? = null,
    @SerialName("timestamp_granularities") val timestampGranularities: List<String>? = null,
    @SerialName("input_audio") val inputAudio: OpenRouterInputAudio
)

@Serializable
internal data class OpenRouterInputAudio(
    val data: String,
    val format: String
)

@Serializable
internal data class OpenRouterErrorEnvelope(
    val error: OpenRouterErrorData? = null
)

@Serializable
internal data class OpenRouterErrorData(
    val message: String? = null
)

// "additionally returns task, language, duration, and segment-level timestamps; only supported by
// OpenAI-compatible providers" e "returns word-level timestamps in the words array" (docs da
// OpenRouter). Um provedor que não conheça os campos costuma responder 400; 422 cobre quem valida o
// corpo antes de processar.
private const val VERBOSE_JSON = "verbose_json"
private val WORD_GRANULARITY = listOf("word")
private val FORMAT_REFUSED = setOf(400, 422)

private val responseJson = Json { ignoreUnknownKeys = true }
