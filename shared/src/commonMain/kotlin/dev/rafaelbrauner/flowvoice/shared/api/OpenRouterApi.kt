package dev.rafaelbrauner.flowvoice.shared.api

import dev.rafaelbrauner.flowvoice.shared.model.ModelInfo

interface OpenRouterApi {
    suspend fun listTranscriptionModels(apiKey: String): List<ModelInfo>
}