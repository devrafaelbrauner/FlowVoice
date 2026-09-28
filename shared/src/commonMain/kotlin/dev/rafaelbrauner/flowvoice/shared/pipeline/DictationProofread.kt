package dev.rafaelbrauner.flowvoice.shared.pipeline

// Troca do ditado direto pelo texto final da passada final (P147; FinalPass, nos dois motores). No modo
// direto cada trecho foi pontuado isolado — no S26 (2026-09-16 09:29) saiu "Hoje o dia está muito
// bonito." / "Por isso iremos para a praia." —, e só no fim do ditado existe a frase inteira. Nada aqui
// fala com a rede nem com o campo: `request` decide se vale pedir a passada final e `replacement` diz o
// que apagar e o que escrever.
object DictationProofread {
    // Teto do ditado trocado. A troca é um `deleteSurroundingText` seguido de um `commitText`: o
    // campo perde o ditado inteiro e o recebe de volta num passo só, e quanto maior o texto, maior o
    // piscar e o estrago de uma troca ruim. 4000 caracteres são ~700 palavras, ou ~5 min de fala —
    // mais que qualquer ditado medido até aqui. Acima disso o ditado fica como está, em vez de
    // trocar só o fim: o corte cairia no meio de uma frase, e é justamente a fronteira entre frases
    // que esta troca existe para consertar.
    const val MAX_CHARS = 4_000

    const val REASON_DISABLED = "desligado"
    const val REASON_EMPTY = "vazio"
    const val REASON_NOT_CONTIGUOUS = "nao_contiguo"
    const val REASON_PENDING = "pendente"
    const val REASON_TOO_LONG = "muito_longo"
    const val REASON_UNCHANGED = "sem_mudanca"
    const val REASON_ERROR = "erro"
    // Sem chave OpenRouter (motor no aparelho): a passada final precisa dela.
    const val REASON_NO_KEY = "sem_chave"
    // A trava de destino (P139) recusou a troca na hora de escrever: o campo já não é o mesmo.
    const val REASON_REFUSED = "recusado"
    // O texto que está no campo não confere com o ditado, nem descontando pontuação (P148).
    const val REASON_FIELD_CHANGED = "campo_diferente"
    // O ditado foi cancelado antes ou durante a revisão (P38): nada vai à OpenRouter.
    const val REASON_CANCELLED = "cancelado"

    sealed interface Request {
        data class Send(val text: String) : Request

        data class Skip(val reason: String) : Request
    }

    sealed interface Outcome {
        // Apaga `deleteBefore` caracteres logo antes do cursor e escreve `text` no lugar.
        data class Replace(val deleteBefore: Int, val text: String) : Outcome

        data class Skip(val reason: String) : Outcome
    }

    // `windowsFailed`: algum trecho ao vivo falhou. Sem nada digitado, é justamente quando a passada final
    // mais importa (NV-qwen, S26 2026-09-28: o provedor não respondeu nenhuma janela e o ditado terminou em
    // "Nenhum trecho transcrito"): ela vai com o rascunho vazio e o texto final entra no campo. Sem trecho
    // falho, rascunho vazio é áudio sem fala, e não há o que pedir.
    fun request(typed: String, pending: String, contiguous: Boolean, enabled: Boolean, windowsFailed: Boolean = false): Request = when {
        !enabled -> Request.Skip(REASON_DISABLED)
        // Com pendente, parte do ditado nem chegou ao campo: o que está lá não é o texto todo. Vem
        // antes do texto vazio porque um ditado inteiro pendente não é um ditado vazio.
        pending.isNotBlank() -> Request.Skip(REASON_PENDING)
        typed.isBlank() && !windowsFailed -> Request.Skip(REASON_EMPTY)
        typed.isBlank() -> Request.Send("")
        // Sem contiguidade o que o FlowVoice escreveu não está mais logo antes do cursor (recusa,
        // "Inserir aqui" noutro campo, digitação do usuário no meio): apagar dali apagaria texto do
        // usuário. É a mesma trava da P144, e aqui pesa mais, porque o apagar é do ditado inteiro.
        !contiguous -> Request.Skip(REASON_NOT_CONTIGUOUS)
        typed.length > MAX_CHARS -> Request.Skip(REASON_TOO_LONG)
        else -> Request.Send(typed)
    }

    // O texto final já passou pela guarda contra a transcrição da nuvem (FinalPass). Contra o rascunho não
    // há guarda de palavra: trocar as palavras erradas do rascunho é o objetivo.
    fun replacement(typed: String, final: String): Outcome = when {
        final.isBlank() -> Outcome.Skip(REASON_EMPTY)
        // Apagar e reescrever o mesmo texto só faria o campo piscar à toa.
        final == typed -> Outcome.Skip(REASON_UNCHANGED)
        else -> Outcome.Replace(deleteBefore = typed.length, text = final)
    }
}
