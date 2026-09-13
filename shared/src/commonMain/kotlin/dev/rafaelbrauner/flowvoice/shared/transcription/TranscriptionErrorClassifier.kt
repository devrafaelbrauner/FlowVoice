package dev.rafaelbrauner.flowvoice.shared.transcription

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

object TranscriptionErrorClassifier {
    fun fromHttpStatus(
        status: Int,
        retryAfterHeader: String? = null,
        bodyMessage: String? = null
    ): TranscriptionError =
        when (status) {
            401, 403 -> TranscriptionError.InvalidKey()
            429 -> TranscriptionError.RateLimit(parseRetryAfter(retryAfterHeader))
            in 500..599 -> TranscriptionError.Server(status)
            else -> TranscriptionError.InvalidResponse(
                buildString {
                    append("http ")
                    append(status)
                    val safe = sanitize(bodyMessage)
                    if (!safe.isNullOrBlank()) {
                        append(' ')
                        append(safe)
                    }
                }
            )
        }

    fun sanitize(message: String?): String? {
        if (message.isNullOrBlank()) return null
        val compact = message.replace('\n', ' ').trim()
        if (compact.contains("sk-", ignoreCase = true) || compact.contains("Bearer", ignoreCase = true)) {
            return null
        }
        return compact.take(80)
    }

    fun fromThrowable(error: Throwable): TranscriptionError {
        if (error is TranscriptionError) return error
        if (error is CancellationException) throw error
        return if (error is HttpRequestTimeoutException || isTimeout(error)) {
            TranscriptionError.Timeout(error)
        } else {
            TranscriptionError.Network(error)
        }
    }

    fun parseRetryAfter(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val seconds = value.trim().toLongOrNull() ?: return null
        if (seconds < 0L) return null
        return (seconds * 1_000L).coerceAtMost(30_000L)
    }

    private fun isTimeout(error: Throwable): Boolean {
        val name = error::class.simpleName.orEmpty()
        val message = error.message.orEmpty()
        return name.contains("Timeout", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true)
    }
}
