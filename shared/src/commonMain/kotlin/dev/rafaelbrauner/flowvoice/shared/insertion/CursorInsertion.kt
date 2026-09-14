package dev.rafaelbrauner.flowvoice.shared.insertion

object CursorInsertion {

    data class Plan(
        val text: String,
        val cursor: Int
    )

    fun plan(
        current: String?,
        showingHint: Boolean,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String
    ): Plan {
        val base = if (showingHint) "" else current.orEmpty()
        val (start, end) = normalizeSelection(base, selectionStart, selectionEnd)
        val text = base.substring(0, start) + insert + base.substring(end)
        return Plan(text = text, cursor = start + insert.length)
    }

    private fun normalizeSelection(base: String, selectionStart: Int, selectionEnd: Int): Pair<Int, Int> {
        val length = base.length
        if (selectionStart !in 0..length || selectionEnd !in 0..length) return length to length
        val start = outsideSurrogatePair(base, minOf(selectionStart, selectionEnd))
        val end = outsideSurrogatePair(base, maxOf(selectionStart, selectionEnd))
        return start to end
    }

    private fun outsideSurrogatePair(base: String, index: Int): Int =
        if (index in 1 until base.length && base[index - 1].isHighSurrogate() && base[index].isLowSurrogate()) {
            index + 1
        } else {
            index
        }
}
