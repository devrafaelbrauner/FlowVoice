package dev.rafaelbrauner.flowvoice.shared.security

import java.util.prefs.Preferences

class DesktopApiKeyStore(
    node: String = "dev.rafaelbrauner.flowvoice"
) : ApiKeyStore {

    private val prefs = Preferences.userRoot().node(node)

    override suspend fun saveApiKey(key: String) {
        prefs.put(API_KEY, key.trim())
        prefs.flush()
    }

    override suspend fun loadApiKey(): String? =
        prefs.get(API_KEY, null)?.trim()?.ifEmpty { null }

    override suspend fun clearApiKey() {
        prefs.remove(API_KEY)
        prefs.flush()
    }

    companion object {
        private const val API_KEY = "openrouter_api_key"
    }
}
