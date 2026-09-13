package dev.rafaelbrauner.flowvoice.shared.dictation

class AudioCaptureException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)