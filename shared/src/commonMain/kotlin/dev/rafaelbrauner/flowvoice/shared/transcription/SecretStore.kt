package dev.rafaelbrauner.flowvoice.shared.transcription

interface SecretStore {
    fun readOpenRouterKey(): String?
    fun writeOpenRouterKey(value: String)
    fun clearOpenRouterKey()
}
