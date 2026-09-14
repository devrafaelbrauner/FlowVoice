package dev.rafaelbrauner.flowvoice.shared.transcription

data class TranscriptionSegment(
    val windowIndex: Int,
    val status: Status,
    val text: String = "",
    val errorKind: String? = null
) {
    enum class Status { Transcribing, Ok, Failed }
}
