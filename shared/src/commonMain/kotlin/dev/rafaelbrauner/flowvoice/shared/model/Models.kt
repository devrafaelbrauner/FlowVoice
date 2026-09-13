package dev.rafaelbrauner.flowvoice.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelInfo(
    val id: String,
    val name: String? = null,
    val contextLength: Int? = null
)

@Serializable
data class OpenRouterModelsResponse(
    val data: List<ModelInfo> = emptyList()
)