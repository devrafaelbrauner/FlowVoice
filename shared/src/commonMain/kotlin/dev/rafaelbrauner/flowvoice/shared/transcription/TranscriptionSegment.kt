package dev.rafaelbrauner.flowvoice.shared.transcription

data class TranscriptionSegment(
    val windowIndex: Int,
    val status: Status,
    val text: String = "",
    val errorKind: String? = null,
    // Quanto áudio da janela anterior foi repetido à frente desta (P143). O começo de `text` pode ser
    // esse trecho transcrito de novo, e é isso que autoriza a deduplicação aproximada da emenda
    // (P150): sem contexto, nada em `text` é repetição.
    val contextDurationMs: Long = 0L
) {
    enum class Status { Transcribing, Ok, Failed }
}
