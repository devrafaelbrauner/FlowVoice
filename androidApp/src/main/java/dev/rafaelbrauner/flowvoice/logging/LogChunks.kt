package dev.rafaelbrauner.flowvoice.logging

// O logcat corta cada linha em ~4 KB; linhas maiores saem numeradas em partes.
object LogChunks {
    fun split(line: String, maxChars: Int): List<String> {
        if (line.length <= maxChars) return listOf(line)
        val pieces = line.chunked(maxChars)
        return pieces.mapIndexed { index, piece -> "(${index + 1}/${pieces.size}) $piece" }
    }
}
