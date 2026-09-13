package dev.rafaelbrauner.flowvoice.shared.api

import dev.rafaelbrauner.flowvoice.shared.model.ModelInfo
import dev.rafaelbrauner.flowvoice.shared.model.OpenRouterModelsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders

class OpenRouterApiClient(
    private val http: HttpClient
) : OpenRouterApi {

    override suspend fun listTranscriptionModels(apiKey: String): List<ModelInfo> =
        http.get(MODELS_ENDPOINT) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
        }.body<OpenRouterModelsResponse>().data

    companion object {
        const val MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models?output_modalities=transcription"
    }
}