package dev.rafaelbrauner.flowvoice.shared.benchmark

object WordErrorRate {
    fun score(reference: String, hypothesis: String): Double {
        val expected = tokenize(reference)
        val actual = tokenize(hypothesis)
        if (expected.isEmpty()) return if (actual.isEmpty()) 0.0 else 1.0
        return levenshtein(expected, actual).toDouble() / expected.size.toDouble()
    }

    fun tokenize(text: String): List<String> =
        text.lowercase()
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    private fun levenshtein(left: List<String>, right: List<String>): Int {
        val rows = left.size + 1
        val cols = right.size + 1
        val distance = Array(rows) { IntArray(cols) }
        for (i in 0 until rows) distance[i][0] = i
        for (j in 0 until cols) distance[0][j] = j
        for (i in 1 until rows) {
            for (j in 1 until cols) {
                val cost = if (left[i - 1] == right[j - 1]) 0 else 1
                distance[i][j] = minOf(
                    distance[i - 1][j] + 1,
                    distance[i][j - 1] + 1,
                    distance[i - 1][j - 1] + cost
                )
            }
        }
        return distance[left.size][right.size]
    }
}
