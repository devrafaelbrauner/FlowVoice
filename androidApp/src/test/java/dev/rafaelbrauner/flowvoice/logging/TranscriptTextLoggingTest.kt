package dev.rafaelbrauner.flowvoice.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptTextLoggingTest {
    private val now = 10 * 60 * 60 * 1_000L
    private val minute = 60 * 1_000L

    @Test
    fun textLoggingNeedsADebuggableBuildAndAFreshMarker() {
        assertTrue(TranscriptTextLogging.isEnabled(debuggable = true, markerModifiedAtMs = now - 10 * minute, nowMs = now))
        assertFalse(TranscriptTextLogging.isEnabled(debuggable = false, markerModifiedAtMs = now - 10 * minute, nowMs = now))
        assertFalse(TranscriptTextLogging.isEnabled(debuggable = true, markerModifiedAtMs = null, nowMs = now))
    }

    @Test
    fun markerExpiresAfterOneHourAndAFutureTimestampDoesNotCount() {
        assertFalse(TranscriptTextLogging.isEnabled(debuggable = true, markerModifiedAtMs = now - 61 * minute, nowMs = now))
        assertFalse(TranscriptTextLogging.isEnabled(debuggable = true, markerModifiedAtMs = now + minute, nowMs = now))
    }

    @Test
    fun longLogLinesAreSplitIntoNumberedPartsThatRebuildTheLine() {
        val line = "proofreading_output text=" + "a".repeat(7_000)

        val parts = LogChunks.split(line, maxChars = 3_000)

        assertEquals(3, parts.size)
        assertTrue(parts.all { it.length <= 3_000 + "(3/3) ".length })
        assertTrue(parts[0].startsWith("(1/3) "))
        assertEquals(line, parts.joinToString("") { it.substringAfter(") ") })
        assertEquals(listOf("curta"), LogChunks.split("curta", maxChars = 3_000))
    }
}
