package dev.rafaelbrauner.flowvoice.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelInfo(
    val id: String,
    val name: String? = null,
    @SerialName("context_length") val contextLength: Int? = null
)

@Serializable
data class OpenRouterModelsResponse(
    val data: List<ModelInfo> = emptyList()
)