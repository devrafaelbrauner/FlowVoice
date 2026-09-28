package dev.rafaelbrauner.flowvoice.shared.preview

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

object LivePreviewAssembler {
    fun assemble(
        segments: List<TranscriptionSegment>,
        sessionComplete: Boolean
    ): LivePreview {
        val merged = segments
            .sortedBy { it.windowIndex }
            .filter { it.status == TranscriptionSegment.Status.Ok && (it.text.isNotBlank() || '\n' in it.text) }
            .fold("") { acc, segment -> mergeAdjacent(acc, segment.text.trim(' '), segment.contextDurationMs, segment.continuous, segment.glued) }

        val transcribing = segments.any { it.status == TranscriptionSegment.Status.Transcribing }
        return if (sessionComplete && !transcribing) {
            LivePreview(finalized = merged, provisional = "")
        } else {
            val suffix = if (transcribing) "…" else ""
            LivePreview(
                finalized = "",
                provisional = listOf(merged, suffix).filter { it.isNotBlank() }.joinToString(" ")
            )
        }
    }

    fun mergeAdjacent(
        left: String,
        right: String,
        contextDurationMs: Long = 0L,
        continuous: Boolean = false,
        glued: Boolean = false
    ): String {
        // A quebra de linha da pontuação falada é texto: só espaço sai das pontas.
        if (left.isBlank() && '\n' !in left) return right.trim(' ')
        if (right.isBlank() && '\n' !in right) return left.trimEnd(' ')
        val match = TranscriptOverlap.match(left, right, contextDurationMs, continuous, glued)
        val head = left.trimEnd(' ')
        return when {
            match.text.isEmpty() -> head
            match.glued || head.endsWith('\n') || match.text.startsWith('\n') -> head + match.text
            else -> "$head ${match.text}"
        }
    }

    // O que de `right` falta depois de `left`, com a mesma deduplicação de mergeAdjacent (P127/P143).
    // Na inserção direta (P139) `left` já está no campo e não se apaga: a sobreposição sai de `right`.
    fun continuation(left: String, right: String, contextDurationMs: Long = 0L): String =
        TranscriptOverlap.match(left, right, contextDurationMs).text
}
