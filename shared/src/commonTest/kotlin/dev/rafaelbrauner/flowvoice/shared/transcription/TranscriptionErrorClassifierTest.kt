package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlin.test.assertFailsWith

class TranscriptionErrorClassifierTest {
    @Test
    fun classifiesUnauthorizedAsInvalidKeyWithoutRetry() {
        val unauthorized = TranscriptionErrorClassifier.fromHttpStatus(401)
        assertIs<TranscriptionError.InvalidKey>(unauthorized)
        assertFalse(unauthorized.isRetryable)
        assertEquals("invalid_key", unauthorized.kind)
    }

    @Test
    fun classifiesForbiddenAsRefusedRequestNotInvalidKeyWithoutRetry() {
        val forbidden = TranscriptionErrorClassifier.fromHttpStatus(403)
        assertFalse(forbidden is TranscriptionError.InvalidKey)
        assertFalse(forbidden.isRetryable)
        assertEquals("forbidden", forbidden.kind)
    }

    @Test
    fun classifiesRequestTimeoutAsRetryableTimeout() {
        val error = TranscriptionErrorClassifier.fromHttpStatus(408, bodyMessage = "Your request timed out")
        assertIs<TranscriptionError.Timeout>(error)
        assertTrue(error.isRetryable)
        assertEquals("timeout", error.kind)
    }

    @Test
    fun classifiesTooManyRequestsWithRetryAfter() {
        val error = TranscriptionErrorClassifier.fromHttpStatus(429, "2")
        assertIs<TranscriptionError.RateLimit>(error)
        assertEquals(2_000L, error.retryAfterMs)
        assertTrue(error.isRetryable)
        assertEquals("rate_limit", error.kind)
    }

    @Test
    fun classifiesServerErrorsAsRetryable() {
        val error = TranscriptionErrorClassifier.fromHttpStatus(503)
        assertIs<TranscriptionError.Server>(error)
        assertEquals(503, error.statusCode)
        assertTrue(error.isRetryable)
    }

    @Test
    fun classifiesOtherHttpAsInvalidResponse() {
        val error = TranscriptionErrorClassifier.fromHttpStatus(422)
        assertIs<TranscriptionError.InvalidResponse>(error)
        assertFalse(error.isRetryable)
    }

    @Test
    fun attachesSanitizedBodyMessageAndDropsSecrets() {
        val withMessage = TranscriptionErrorClassifier.fromHttpStatus(
            400,
            bodyMessage = "Invalid request parameters"
        )
        assertEquals("http 400 Invalid request parameters", withMessage.message)
        val withSecret = TranscriptionErrorClassifier.fromHttpStatus(
            400,
            bodyMessage = "key sk-or-v1-secret failed"
        )
        assertEquals("http 400", withSecret.message)
    }

    @Test
    fun classifiesTimeoutMessages() {
        val error = TranscriptionErrorClassifier.fromThrowable(IllegalStateException("request timed out"))
        assertIs<TranscriptionError.Timeout>(error)
        assertTrue(error.isRetryable)
    }

    @Test
    fun classifiesUnknownThrowablesAsNetwork() {
        val error = TranscriptionErrorClassifier.fromThrowable(IllegalStateException("connection reset"))
        assertIs<TranscriptionError.Network>(error)
        assertTrue(error.isRetryable)
    }

    @Test
    fun rethrowsCancellation() {
        assertFailsWith<CancellationException> {
            TranscriptionErrorClassifier.fromThrowable(CancellationException("cancelled"))
        }
    }

    @Test
    fun parseRetryAfterAcceptsSecondsAndRejectsJunk() {
        assertEquals(5_000L, TranscriptionErrorClassifier.parseRetryAfter("5"))
        assertEquals(30_000L, TranscriptionErrorClassifier.parseRetryAfter("120"))
        assertNull(TranscriptionErrorClassifier.parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT"))
        assertNull(TranscriptionErrorClassifier.parseRetryAfter(null))
        assertNull(TranscriptionErrorClassifier.parseRetryAfter("-1"))
    }
}
