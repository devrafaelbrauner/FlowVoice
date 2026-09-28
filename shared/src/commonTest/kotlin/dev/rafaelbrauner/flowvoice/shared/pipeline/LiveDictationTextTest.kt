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
        val preview = LiveDictationText.split(listOf(ok(0, "o médico pediu"), ok(1, "pediu o exame", 1_000L)))

        assertEquals("o médico pediu", preview.finalized)
        assertEquals("o exame", preview.provisional)
    }

    @Test
    fun segmentsAreOrderedByWindow() {
        val preview = LiveDictationText.split(listOf(ok(1, "o exame"), ok(0, "pediu")))

        assertEquals("pediu", preview.finalized)
        assertEquals("o exame", preview.provisional)
    }

    // N1: com contexto sobreposto (P143), a emenda aproximada da P150 vale também no texto ao vivo. A
    // prévia da revisão mostrava "muito bonito" duas vezes, que o texto final já não tinha.
    @Test
    fun theLivePreviewDeduplicatesTheContextLikeTheFinalText() {
        val segments = listOf(
            ok(0, "Hoje o dia está muito bonito."),
            TranscriptionSegment(1, TranscriptionSegment.Status.Ok, "Tá muito bonito, por isso iremos à praia.", contextDurationMs = 1_000),
            TranscriptionSegment(2, TranscriptionSegment.Status.Transcribing)
        )

        val preview = LiveDictationText.split(segments)

        assertEquals("Hoje o dia está muito bonito.", preview.finalized)
        assertEquals("por isso iremos à praia. …", preview.provisional)
    }

    @Test
    fun noSegmentsProduceEmptyText() {
        val preview = LiveDictationText.split(emptyList())

        assertEquals("", preview.finalized)
        assertEquals("", preview.provisional)
    }

    private fun ok(index: Int, text: String, contextDurationMs: Long = 0L) =
        TranscriptionSegment(index, TranscriptionSegment.Status.Ok, text, contextDurationMs = contextDurationMs)
}
