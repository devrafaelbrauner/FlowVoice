package dev.rafaelbrauner.flowvoice.shared.benchmark

object TranscriptionShortlist {
    val roundOne = listOf(
        "openai/whisper-large-v3-turbo",
        "openai/gpt-4o-mini-transcribe",
        "openai/gpt-transcribe",
        "openai/gpt-4o-transcribe",
        "deepgram/nova-3",
        "microsoft/mai-transcribe-2",
        "nvidia/parakeet-tdt-0.6b-v3",
        "mistralai/voxtral-mini-transcribe"
    )

    const val BASELINE = "openai/whisper-large-v3-turbo"
}
