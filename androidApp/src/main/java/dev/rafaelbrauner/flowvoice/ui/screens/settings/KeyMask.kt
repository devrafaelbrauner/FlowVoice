package dev.rafaelbrauner.flowvoice.ui.screens.settings

object KeyMask {
    private const val DOTS = "••••"
    private const val VISIBLE_SUFFIX = 4
    private const val MIN_LENGTH_FOR_SUFFIX = 20
    private val knownPrefixes = listOf("sk-or-v1-", "sk-")

    fun mask(key: String?): String? {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val prefix = knownPrefixes.firstOrNull { trimmed.startsWith(it) && trimmed.length > it.length }.orEmpty()
        val suffix = if (trimmed.length >= MIN_LENGTH_FOR_SUFFIX) trimmed.takeLast(VISIBLE_SUFFIX) else ""
        return prefix + DOTS + suffix
    }
}
