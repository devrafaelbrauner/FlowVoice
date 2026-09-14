package dev.rafaelbrauner.flowvoice.shared.benchmark

data class BenchmarkBudget(
    val maxUsd: Double = DEFAULT_MAX_USD,
    val maxRequests: Int = DEFAULT_MAX_REQUESTS
) {
    init {
        require(maxUsd > 0.0) { "maxUsd must be positive" }
        require(maxRequests > 0) { "maxRequests must be positive" }
    }

    companion object {
        const val DEFAULT_MAX_USD = 1.0
        const val DEFAULT_MAX_REQUESTS = 40
    }
}
