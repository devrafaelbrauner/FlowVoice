package dev.rafaelbrauner.flowvoice.shared.security

interface ApiKeyStore {
    suspend fun saveApiKey(key: String)
    suspend fun loadApiKey(): String?
    suspend fun clearApiKey()
}

fun sanitizeApiKey(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.length < 8) return null
    return trimmed
}
