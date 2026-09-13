package dev.rafaelbrauner.flowvoice.shared.transcription

internal object RetryDelay {
    fun exponential(
        attempt: Int,
        initialBackoffMs: Long,
        maxBackoffMs: Long,
        jitterUnit: Double
    ): Long {
        val shift = attempt.coerceIn(0, 16)
        val exponential = (initialBackoffMs * (1L shl shift)).coerceAtMost(maxBackoffMs)
        val jitter = (exponential * jitterUnit.coerceIn(0.0, 1.0)).toLong()
        return (exponential / 2) + (jitter / 2)
    }
}
