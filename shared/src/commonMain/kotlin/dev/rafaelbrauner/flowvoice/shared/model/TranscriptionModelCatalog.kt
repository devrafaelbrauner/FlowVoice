package dev.rafaelbrauner.flowvoice.shared.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class TranscriptionModelCatalog(
    private val api: dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApi,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val cacheTtl: Duration = CACHE_TTL
) {
    private var cached: List<ModelInfo>? = null
    private var cachedAt: TimeMark? = null
    private var keyFingerprint: Int? = null

    suspend fun load(apiKey: String?): TranscriptionCatalogResult {
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty()) return TranscriptionCatalogResult.Failed(CatalogFailure.NO_KEY)
        keyFingerprint?.let { known ->
            if (known != keyFingerprintOf(key)) {
                cached = null
                cachedAt = null
            }
        }
        cached?.let { models ->
            if (cachedAt?.elapsedNow()?.let { it < cacheTtl } == true) return wrap(models)
        }
        val models = try {
            api.listTranscriptionModels(key)
        } catch (error: Exception) {
            return mapFailure(error) ?: TranscriptionCatalogResult.Failed(CatalogFailure.UNAVAILABLE)
        }
        cached = models
        cachedAt = timeSource.markNow()
        keyFingerprint = keyFingerprintOf(key)
        return wrap(models)
    }

    fun cached(): TranscriptionCatalogResult? =
        cached?.let { wrap(it) }

    fun clear() {
        cached = null
        cachedAt = null
        keyFingerprint = null
    }

    private fun wrap(models: List<ModelInfo>): TranscriptionCatalogResult {
        val normalized = TranscriptionCatalog.normalize(models)
        if (normalized.isEmpty()) return TranscriptionCatalogResult.Empty
        return TranscriptionCatalogResult.Ready(normalized)
    }

    private fun mapFailure(error: Throwable): TranscriptionCatalogResult? {
        var cause: Throwable? = error
        while (cause != null) {
            val name = cause::class.simpleName.orEmpty()
            if (name == "InvalidKey" || name == "Forbidden") {
                return TranscriptionCatalogResult.Failed(CatalogFailure.INVALID_KEY)
            }
            cause = cause.cause
        }
        val message = error.message.orEmpty()
        if ("401" in message || "403" in message) {
            return TranscriptionCatalogResult.Failed(CatalogFailure.INVALID_KEY)
        }
        return null
    }

    companion object {
        val CACHE_TTL: Duration = 24.hours

        internal fun keyFingerprintOf(key: String): Int =
            key.trim().let { "${it.length}:${it.take(3)}:${it.takeLast(3)}" }.hashCode()
    }
}
