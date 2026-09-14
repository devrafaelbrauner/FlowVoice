package dev.rafaelbrauner.flowvoice.shared.transcription

fun interface TranscriptionEventLog {
    fun log(event: String, metadata: Map<String, String>)

    companion object {
        val NoOp: TranscriptionEventLog = TranscriptionEventLog { _, _ -> }
    }
}
