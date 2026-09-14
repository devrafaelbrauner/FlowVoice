package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveDictationTextTest {
    @Test
    fun latestTranscribedWindowIsProvisionalAndEarlierOnesAreFinalized() {
        val preview = LiveDictationText.split(
            listOf(ok(0, "consegui fechar"), ok(1, "o orçamento"), ok(2, "do projeto"))
        )

        assertEquals("consegui fechar o orçamento", preview.finalized)
        assertEquals("do projeto", preview.provisional)
    }

    @Test
    fun transcribingWindowAddsPendingMark() {
        val preview = LiveDictationText.split(
            listOf(ok(0, "bom dia"), TranscriptionSegment(1, TranscriptionSegment.Status.Transcribing))
        )

        assertEquals("", preview.finalized)
        assertEquals("bom dia …", preview.provisional)
    }

    @Test
    fun failedWindowsAreSkipped() {
        val preview = LiveDictationText.split(
            listOf(ok(0, "o médico"), TranscriptionSegment(1, TranscriptionSegment.Status.Failed), ok(2, "de sangue"))
        )

        assertEquals("o médico", preview.finalized)
        assertEquals("de sangue", preview.provisional)
    }

    @Test
    fun overlapWithFinalizedTextIsNotRepeated() {
        val preview = LiveDictationText.split(listOf(ok(0, "o médico pediu"), ok(1, "pediu o exame")))

        assertEquals("o médico pediu", preview.finalized)
        assertEquals("o exame", preview.provisional)
    }

    @Test
    fun segmentsAreOrderedByWindow() {
        val preview = LiveDictationText.split(listOf(ok(1, "o exame"), ok(0, "pediu")))

        assertEquals("pediu", preview.finalized)
        assertEquals("o exame", preview.provisional)
    }

    @Test
    fun noSegmentsProduceEmptyText() {
        val preview = LiveDictationText.split(emptyList())

        assertEquals("", preview.finalized)
        assertEquals("", preview.provisional)
    }

    private fun ok(index: Int, text: String) =
        TranscriptionSegment(index, TranscriptionSegment.Status.Ok, text)
}
