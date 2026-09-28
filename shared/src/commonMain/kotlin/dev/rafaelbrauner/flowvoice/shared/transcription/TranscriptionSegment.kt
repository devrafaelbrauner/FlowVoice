package dev.rafaelbrauner.flowvoice.shared.transcription

data class TranscriptionSegment(
    val windowIndex: Int,
    val status: Status,
    val text: String = "",
    val errorKind: String? = null,
    // Quanto áudio da janela anterior foi repetido à frente desta (P143). O começo de `text` pode ser
    // esse trecho transcrito de novo, e é isso que autoriza a deduplicação aproximada da emenda
    // (P150): sem contexto, nada em `text` é repetição.
    val contextDurationMs: Long = 0L,
    // Pedaço de um fluxo contínuo (motor no aparelho): o texto começa exatamente onde o anterior
    // terminou, sem áudio repetido. Nada nele é repetição, nem uma palavra igual à última do pedaço
    // anterior — é o usuário falando de novo, e a deduplicação da emenda não se aplica.
    val continuous: Boolean = false,
    // Só no fluxo contínuo: o pedaço começa no meio da última palavra do anterior ou com a pontuação
    // dela (o primeiro token não abre palavra), e entra colado, sem espaço.
    val glued: Boolean = false
) {
    enum class Status { Transcribing, Ok, Failed }
}
