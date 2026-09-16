package dev.rafaelbrauner.flowvoice.shared.preview

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class LivePreviewAssemblerTest {
    @Test
    fun mergesOverlappingWindowsWithoutDuplicatingTokens() {
        val segments = listOf(
            ok(0, "o médico pediu o exame"),
            ok(1, "o exame de sangue para amanhã")
        )
        val preview = LivePreviewAssembler.assemble(segments, sessionComplete = true)
        assertEquals("o médico pediu o exame de sangue para amanhã", preview.finalized)
        assertEquals("", preview.provisional)
    }

    @Test
    fun activeSessionKeepsTextProvisional() {
        val segments = listOf(ok(0, "olá mundo"))
        val preview = LivePreviewAssembler.assemble(segments, sessionComplete = false)
        assertEquals("", preview.finalized)
        assertEquals("olá mundo", preview.provisional)
    }

    @Test
    fun transcribingAppendsEllipsisAsProvisional() {
        val segments = listOf(
            ok(0, "olá"),
            TranscriptionSegment(1, TranscriptionSegment.Status.Transcribing)
        )
        val preview = LivePreviewAssembler.assemble(segments, sessionComplete = false)
        assertEquals("olá …", preview.provisional)
        assertEquals("", preview.finalized)
    }

    @Test
    fun skipsFailedWindowsAndKeepsOrder() {
        val segments = listOf(
            ok(0, "um"),
            TranscriptionSegment(1, TranscriptionSegment.Status.Failed, errorKind = "network"),
            ok(2, "três")
        )
        val preview = LivePreviewAssembler.assemble(segments, sessionComplete = true)
        assertEquals("um três", preview.finalized)
    }

    @Test
    fun emptySegmentsYieldEmptyPreview() {
        val preview = LivePreviewAssembler.assemble(emptyList(), sessionComplete = true)
        assertEquals("", preview.finalized)
        assertEquals("", preview.provisional)
        assertEquals("", preview.full)
    }

    @Test
    fun continuationDropsFromTheNewPieceTheWordsThatRepeatTheEndOfTheTextAlreadyTyped() {
        assertEquals("de sangue", LivePreviewAssembler.continuation("o médico pediu o exame", "o exame de sangue"))
        assertEquals("importante", LivePreviewAssembler.continuation("é muito", "muito importante"))
        assertEquals("", LivePreviewAssembler.continuation("pediu o exame", "o exame"))
    }

    @Test
    fun continuationOfTheFirstPieceIsTheTrimmedPieceAndABlankPieceAddsNothing() {
        assertEquals("olá mundo", LivePreviewAssembler.continuation("", "  olá mundo "))
        assertEquals("", LivePreviewAssembler.continuation("olá", "   "))
    }

    @Test
    fun typingEachContinuationWithASpaceGivesTheSameTextAsMergingTheWindows() {
        val windows = listOf("o médico pediu", "pediu o exame", "de sangue para", "para amanhã cedo")
        val merged = windows.fold("") { acc, next -> LivePreviewAssembler.mergeAdjacent(acc, next) }
        val typed = windows.fold("") { acc, next ->
            val piece = LivePreviewAssembler.continuation(acc, next)
            if (acc.isEmpty() || piece.isEmpty()) acc + piece else "$acc $piece"
        }

        assertEquals(merged, typed)
    }

    private fun ok(index: Int, text: String) =
        TranscriptionSegment(index, TranscriptionSegment.Status.Ok, text)
}
