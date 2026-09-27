package dev.rafaelbrauner.flowvoice.shared.api

import dev.rafaelbrauner.flowvoice.shared.model.ModelInfo
import dev.rafaelbrauner.flowvoice.shared.model.OpenRouterModelsResponse
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionErrorClassifier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

class OpenRouterApiClient(
    private val http: HttpClient
) : OpenRouterApi {

    // Y9: sem conferir o status, um 429 ou 5xx com corpo JSON de erro virava lista vazia (`data`
    // tem padrão `[]`), e o catálogo a guardava por 24 h. O erro sai classificado como o da
    // transcrição: 401 é chave inválida, o resto é serviço indisponível.
    override suspend fun listTranscriptionModels(apiKey: String): List<ModelInfo> {
        val response = http.get(MODELS_ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            throw TranscriptionErrorClassifier.fromHttpStatus(
                response.status.value,
                response.headers[HttpHeaders.RetryAfter]
            )
        }
        return response.body<OpenRouterModelsResponse>().data
    }

    companion object {
        const val MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models?output_modalities=transcription"
    }
}
