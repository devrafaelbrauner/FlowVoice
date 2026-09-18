package dev.rafaelbrauner.flowvoice.shared.api

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionApi
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionRequest
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionResponse
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.core.writeFully
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class OpenRouterTranscriptionClient(
    http: HttpClient,
    private val maxRetries: Int = 1,
    private val retryDelayMs: Long = 1000
) : TranscriptionApi {

    private val plainHttp: HttpClient = HttpClient(http.engine) {
        expectSuccess = false
        install(io.ktor.client.plugins.HttpTimeout)
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override suspend fun transcribe(
        request: TranscriptionRequest,
        apiKey: String
    ): TranscriptionResult {
        val key = apiKey.trim()
        if (key.isEmpty()) throw TranscriptionError.MissingApiKey()
        val started = currentTimeMs()
        var attempt = 0
        while (true) {
            val response: HttpResponse = try {
                plainHttp.post(TRANSCRIBE_ENDPOINT) {
                    header(HttpHeaders.Authorization, "Bearer $key")
                    contentType(ContentType.Application.Json)
                    timeout { requestTimeoutMillis = 60_000 }
                    setBody(
                        SttJsonRequest(
                            inputAudio = SttInputAudio(
                                data = base64Of(request.audio),
                                format = "wav"
                            ),
                            model = request.model,
                            language = request.language,
                            responseFormat = "json",
                            temperature = request.temperature
                        )
                    )
                }
            } catch (e: HttpRequestTimeoutException) {
                throw TranscriptionError.Timeout()
            } catch (e: TranscriptionError) {
                throw e
            } catch (e: Exception) {
                throw TranscriptionError.NetworkFailure(e)
            }
            val status = response.status.value
            val rawBody: String = try {
                response.bodyAsText()
            } catch (e: Exception) {
                if (status == 401) throw TranscriptionError.InvalidApiKey()
                if (status == 402) throw TranscriptionError.NoCredits()
                if (status == 404) throw TranscriptionError.ModelNotFound(request.model)
                if (status == 429) {
                    if (attempt >= maxRetries) throw TranscriptionError.RateLimited()
                    attempt += 1
                    delay(retryDelayMs)
                    continue
                }
                if (status in 500..599) {
                    if (attempt >= maxRetries) throw TranscriptionError.ServerError(status)
                    attempt += 1
                    delay(retryDelayMs)
                    continue
                }
                throw TranscriptionError.NetworkFailure(e)
            }
            if (status == 401) throw TranscriptionError.InvalidApiKey()
            if (status == 402) throw TranscriptionError.NoCredits()
            if (status == 404) throw TranscriptionError.ModelNotFound(request.model)
            if (status == 429) {
                if (attempt >= maxRetries) throw TranscriptionError.RateLimited()
                attempt += 1
                delay(retryDelayMs)
                continue
            }
            if (status in 500..599) {
                if (attempt >= maxRetries) throw TranscriptionError.ServerError(status)
                attempt += 1
                delay(retryDelayMs)
                continue
            }
            if (status == 400) throw TranscriptionError.BadRequest(rawBody.take(300))
            if (status !in 200..299) throw TranscriptionError.ServerError(status)
            val parsed: TranscriptionResponse = try {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<TranscriptionResponse>(rawBody)
            } catch (e: Exception) {
                throw TranscriptionError.NetworkFailure(e)
            }
            return TranscriptionResult(
                text = parsed.text,
                outputLanguage = outputLanguageFor(request.language),
                modelId = request.model,
                latencyMs = currentTimeMs() - started
            )
        }
    }

    companion object {
        const val TRANSCRIBE_ENDPOINT = "https://openrouter.ai/api/v1/audio/transcriptions"

        internal fun outputLanguageFor(language: String): dev.rafaelbrauner.flowvoice.shared.transcription.OutputLanguage =
            if (language.lowercase().startsWith("pt")) {
                dev.rafaelbrauner.flowvoice.shared.transcription.OutputLanguage.PT
            } else {
                dev.rafaelbrauner.flowvoice.shared.transcription.OutputLanguage.UNKNOWN
            }

        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
        internal fun base64Of(bytes: ByteArray): String =
            kotlin.io.encoding.Base64.encode(bytes)

        @kotlinx.serialization.Serializable
        internal data class SttInputAudio(val data: String, val format: String)

        @kotlinx.serialization.Serializable
        internal data class SttJsonRequest(
            @kotlinx.serialization.SerialName("input_audio") val inputAudio: SttInputAudio,
            val model: String,
            val language: String,
            @kotlinx.serialization.SerialName("response_format") val responseFormat: String = "json",
            val temperature: Float? = null
        )

        fun wavBytes(samples: FloatArray, sampleRateHz: Int = 16000): ByteArray {
            val dataSize = samples.size * 2
            val packet = buildPacket {
                writeFully("RIFF".toByteArray())
                writeIntLe(36 + dataSize)
                writeFully("WAVE".toByteArray())
                writeFully("fmt ".toByteArray())
                writeIntLe(16)
                writeShortLe(1)
                writeShortLe(1)
                writeIntLe(sampleRateHz)
                writeIntLe(sampleRateHz * 2)
                writeShortLe(2)
                writeShortLe(16)
                writeFully("data".toByteArray())
                writeIntLe(dataSize)
                for (sample in samples) {
                    val clamped = sample.coerceIn(-1f, 1f)
                    writeShortLe((clamped * 32767).toInt())
                }
            }
            return packet.readBytes()
        }

        private fun io.ktor.utils.io.core.BytePacketBuilder.writeIntLe(value: Int) {
            writeByte((value and 0xFF).toByte())
            writeByte(((value shr 8) and 0xFF).toByte())
            writeByte(((value shr 16) and 0xFF).toByte())
            writeByte(((value shr 24) and 0xFF).toByte())
        }

        private fun io.ktor.utils.io.core.BytePacketBuilder.writeShortLe(value: Int) {
            writeByte((value and 0xFF).toByte())
            writeByte(((value shr 8) and 0xFF).toByte())
        }
    }
}

internal expect fun currentTimeMs(): Long
