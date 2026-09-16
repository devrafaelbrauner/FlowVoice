package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
        val wav = WavEncoder.encode(window.transmittedPcm, window.format)
        val requestBody = OpenRouterSttRequest(
            model = usedModel,
            language = config.language,
            temperature = config.temperature,
            inputAudio = OpenRouterInputAudio(
                data = wav.encodeBase64(),
                format = "wav"
            )
        )
        var httpStatus: Int? = null
        return try {
            val response = http.post("${config.baseUrl}${config.transcriptionsPath}") {
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
            val status = response.status.value
            val raw = response.bodyAsText()
            if (!response.status.isSuccess()) {
                httpStatus = status
                throw TranscriptionErrorClassifier.fromHttpStatus(
                    status,
                    response.headers[HttpHeaders.RetryAfter],
                    extractErrorMessage(raw)
                )
            }
            val payload = try {
                responseJson.decodeFromString(OpenRouterTranscriptionResponse.serializer(), raw)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                throw TranscriptionError.InvalidResponse("unparseable json")
            }
            val text = payload.text?.trim().orEmpty()
            val result = TranscriptionResult(
                text = text,
                model = usedModel,
                durationMs = window.durationMs,
                costUsd = payload.usage?.cost
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
    @SerialName("input_audio") val inputAudio: OpenRouterInputAudio
)

@Serializable
internal data class OpenRouterInputAudio(
    val data: String,
    val format: String
)

@Serializable
internal data class OpenRouterTranscriptionResponse(
    val text: String? = null,
    val usage: OpenRouterTranscriptionUsage? = null
)

@Serializable
internal data class OpenRouterTranscriptionUsage(
    val cost: Double? = null
)

@Serializable
internal data class OpenRouterErrorEnvelope(
    val error: OpenRouterErrorData? = null
)

@Serializable
internal data class OpenRouterErrorData(
    val message: String? = null
)

private val responseJson = Json { ignoreUnknownKeys = true }
