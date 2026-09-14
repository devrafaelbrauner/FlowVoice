package dev.rafaelbrauner.flowvoice.shared.preview

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

object LivePreviewAssembler {
    fun assemble(
        segments: List<TranscriptionSegment>,
        sessionComplete: Boolean
    ): LivePreview {
        val merged = segments
            .sortedBy { it.windowIndex }
            .filter { it.status == TranscriptionSegment.Status.Ok && it.text.isNotBlank() }
            .map { it.text.trim() }
            .fold("") { acc, next -> mergeAdjacent(acc, next) }

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

    fun mergeAdjacent(left: String, right: String): String {
        if (left.isBlank()) return right.trim()
        if (right.isBlank()) return left.trim()
        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)
        val overlap = overlapSize(leftTokens, rightTokens)
        val mergedTokens = leftTokens + rightTokens.drop(overlap)
        return mergedTokens.joinToString(" ")
    }

    private fun overlapSize(left: List<String>, right: List<String>): Int {
        val max = minOf(left.size, right.size)
        for (size in max downTo 1) {
            if (left.takeLast(size) == right.take(size)) return size
        }
        return 0
    }

    private fun tokenize(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }
}
