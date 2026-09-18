package dev.rafaelbrauner.flowvoice.shared.model

sealed class TranscriptionCatalogResult {
    data class Ready(val models: List<ModelInfo>) : TranscriptionCatalogResult()
    data object Empty : TranscriptionCatalogResult()
    data class Failed(val reason: CatalogFailure) : TranscriptionCatalogResult()
}

enum class CatalogFailure {
    NO_KEY,
    INVALID_KEY,
    UNAVAILABLE
}

object TranscriptionCatalog {
    fun normalize(models: List<ModelInfo>): List<ModelInfo> {
        val seen = linkedMapOf<String, ModelInfo>()
        models.forEach { model ->
            val id = model.id.trim()
            if (id.isEmpty()) return@forEach
            val current = seen[id]
            if (current == null || (current.name.isNullOrBlank() && !model.name.isNullOrBlank())) {
                seen[id] = ModelInfo(id = id, name = model.name, contextLength = model.contextLength)
            }
        }
        return seen.values.sortedBy { it.id }
    }
}
