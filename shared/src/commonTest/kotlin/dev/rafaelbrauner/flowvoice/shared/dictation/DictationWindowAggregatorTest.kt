package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DictationWindowAggregatorTest {
    private fun frameOf(durationMs: Long): AudioFrame =
        AudioFrame(ByteArray((durationMs * 32).toInt()), AudioFormat.DEFAULT)

    @Test
    fun emitsWindowWhenTargetDurationIsReached() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        val windows = aggregator.onFrame(frameOf(100L))

        assertEquals(1, windows.size)
        val window = windows.single()
        assertEquals(0, window.index)
        assertEquals(AudioFormat.DEFAULT, window.format)
        assertEquals(0L, window.startedAtMs)
        assertEquals(100L, window.finishedAtMs)
        assertEquals(100L, window.durationMs)
        assertContentEquals(ByteArray(3_200), window.pcm)
    }

    @Test
    fun returnsEmptyBeforeTargetDuration() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 300L)

        val first = aggregator.onFrame(frameOf(100L))
        val second = aggregator.onFrame(frameOf(100L))
        val third = aggregator.onFrame(frameOf(100L))

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals(1, third.size)
        assertEquals(300L, third.single().durationMs)
    }

    @Test
    fun windowIndexAndTimelineContinueAcrossWindows() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        val windows = List(4) { aggregator.onFrame(frameOf(100L)) }.flatten()

        assertEquals(listOf(0, 1, 2, 3), windows.map { it.index })
        assertEquals(listOf(0L, 100L, 200L, 300L), windows.map { it.startedAtMs })
        assertEquals(listOf(100L, 200L, 300L, 400L), windows.map { it.finishedAtMs })
    }

    @Test
    fun mismatchedFormatThrows() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)
        val frame = AudioFrame(ByteArray(1_600), AudioFormat(sampleRate = 8_000))

        assertFailsWith<IllegalArgumentException> { aggregator.onFrame(frame) }
    }

    @Test
    fun emptyFrameDoesNotProduceWindow() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        val windows = aggregator.onFrame(AudioFrame(ByteArray(0)))

        assertTrue(windows.isEmpty())
    }

    @Test
    fun clearResetsBufferAndTimeline() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 300L)

        aggregator.onFrame(frameOf(100L))
        aggregator.onFrame(frameOf(100L))
        aggregator.clear()

        val first = aggregator.onFrame(frameOf(100L))
        val second = aggregator.onFrame(frameOf(100L))
        val third = aggregator.onFrame(frameOf(100L)).single()

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals(0, third.index)
        assertEquals(0L, third.startedAtMs)
    }

    @Test
    fun windowValidationThrows() {
        assertFailsWith<IllegalArgumentException> {
            DictationWindow(index = -1, pcm = ByteArray(0), format = AudioFormat.DEFAULT, startedAtMs = 0L, finishedAtMs = 10L)
        }
        assertFailsWith<IllegalArgumentException> {
            DictationWindow(index = 0, pcm = ByteArray(0), format = AudioFormat.DEFAULT, startedAtMs = 10L, finishedAtMs = 0L)
        }
    }

    @Test
    fun flushEmitsBufferedTailAsFinalWindow() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        aggregator.onFrame(frameOf(100L))
        aggregator.onFrame(frameOf(50L))
        val window = aggregator.flush()

        assertNotNull(window)
        assertEquals(1, window.index)
        assertEquals(100L, window.startedAtMs)
        assertEquals(150L, window.finishedAtMs)
        assertContentEquals(ByteArray(1_600), window.pcm)
    }

    @Test
    fun timelineContinuesAfterFlush() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        aggregator.onFrame(frameOf(50L))
        val flushed = aggregator.flush()
        val next = aggregator.onFrame(frameOf(100L)).single()

        assertNotNull(flushed)
        assertEquals(0, flushed.index)
        assertEquals(0L, flushed.startedAtMs)
        assertEquals(50L, flushed.finishedAtMs)
        assertEquals(1, next.index)
        assertEquals(50L, next.startedAtMs)
        assertEquals(150L, next.finishedAtMs)
    }

    @Test
    fun flushWithoutBufferedAudioReturnsNull() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        assertNull(aggregator.flush())
    }
}