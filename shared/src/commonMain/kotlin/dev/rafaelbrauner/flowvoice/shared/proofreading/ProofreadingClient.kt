package dev.rafaelbrauner.flowvoice.shared.proofreading

interface ProofreadingClient {
    suspend fun proofread(text: String, apiKey: String, model: String): String
}
