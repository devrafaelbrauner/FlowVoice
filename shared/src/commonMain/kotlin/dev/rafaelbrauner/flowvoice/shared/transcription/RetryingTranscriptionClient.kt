package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

class RetryingTranscriptionClient(
    private val delegate: TranscriptionClient,
    private val config: OpenRouterConfig,
    private val sleeper: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val random: () -> Double = { Random.nextDouble() },
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp
) : TranscriptionClient {
    override suspend fun transcribe(
        window: DictationWindow,
        apiKey: String,
        model: String?
    ): TranscriptionResult {
        var attempt = 0
        while (true) {
            try {
                return delegate.transcribe(window, apiKey, model)
            } catch (error: CancellationException) {
                throw error
            } catch (error: TranscriptionError) {
                if (!error.isRetryable || attempt >= config.maxRetries) {
                    throw error
                }
                val delayMs = delayFor(error, attempt)
                attempt++
                eventLog.log(
                    "transcription_retry",
                    mapOf(
                        "window" to window.index.toString(),
                        "attempt" to attempt.toString(),
                        "kind" to error.kind,
                        "delayMs" to delayMs.toString()
                    )
                )
                sleeper(delayMs)
            }
        }
    }

    override fun cancel() {
        delegate.cancel()
    }

    private fun delayFor(error: TranscriptionError, attempt: Int): Long {
        if (error is TranscriptionError.RateLimit) {
            return error.retryAfterMs ?: RetryDelay.exponential(
                attempt,
                config.initialBackoffMs,
                config.maxBackoffMs,
                random()
            )
        }
        return RetryDelay.exponential(
            attempt,
            config.initialBackoffMs,
            config.maxBackoffMs,
            random()
        )
    }
}
