package dev.rafaelbrauner.flowvoice.shared.transcription

// Resposta da transcrição, nos dois formatos que a OpenRouter aceita (P146):
//  - `json` (padrão): só `text` e `usage`;
//  - `verbose_json` com `timestamp_granularities: ["word"]`: além do texto, `words` e/ou `segments`
//    com tempos. A documentação avisa que `verbose_json` "only supported by OpenAI-compatible
//    providers" e que as palavras voltam "when the provider returns them" — ou seja, os tempos são
//    opcionais mesmo quando o pedido é aceito.
//
// Um único tipo serve aos dois formatos: sem tempos, as listas ficam vazias.
data class TranscriptionPayload(
    val text: String,
    val words: List<TimedUnit> = emptyList(),
    val segments: List<TimedUnit> = emptyList(),
    val costUsd: Double? = null
) {
    val hasTimes: Boolean
        get() = words.isNotEmpty() || segments.isNotEmpty()
}

// Palavra ou segmento com início e fim em milissegundos, contados do começo do áudio enviado — que,
// com o contexto sobreposto da P143, começa **antes** da janela.
data class TimedUnit(
    val text: String,
    val startMs: Long,
    val endMs: Long
)
