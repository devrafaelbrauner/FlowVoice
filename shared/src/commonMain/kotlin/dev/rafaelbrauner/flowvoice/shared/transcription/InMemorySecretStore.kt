package dev.rafaelbrauner.flowvoice.shared.transcription

class InMemorySecretStore : SecretStore {
    private var key: String? = null

    override fun readOpenRouterKey(): String? = key

    override fun writeOpenRouterKey(value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "key must not be blank" }
        key = trimmed
    }

    override fun clearOpenRouterKey() {
        key = null
    }
}
