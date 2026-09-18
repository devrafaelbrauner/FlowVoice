package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingGuard

// Revisão final do ditado direto (P147). No modo direto o modelo enxerga uma janela de 2 a 4 s por
// vez, fecha cada uma com ponto e abre a seguinte com maiúscula: no S26 (2026-09-16 09:29) saiu
// "Hoje o dia está muito bonito." / "Por isso iremos para a praia." Só no fim do ditado existe a
// frase inteira, e é aí que a pontuação pode ser decidida. Nada aqui fala com a rede nem com o campo:
// `request` decide se vale pedir a revisão e `outcome` diz o que apagar e o que escrever.
object DictationProofread {
    // Teto do ditado revisado. A troca é um `deleteSurroundingText` seguido de um `commitText`: o
    // campo perde o ditado inteiro e o recebe de volta num passo só, e quanto maior o texto, maior o
    // piscar e o estrago de uma troca ruim. 4000 caracteres são ~700 palavras, ou ~5 min de fala —
    // mais que qualquer ditado medido até aqui. Acima disso o ditado fica como está, em vez de
    // revisar só o fim: o corte cairia no meio de uma frase, e é justamente a fronteira entre frases
    // que esta revisão existe para consertar.
    const val MAX_CHARS = 4_000

    // Teto de espera da revisão final (P152). Sem ele valeria o `requestTimeoutMs` do
    // `OpenRouterConfig` (30 s), com o ditado já no campo e o usuário parado esperando o texto trocar.
    // As duas chamadas medidas no S26 (2026-09-16, 09:29 e 10:42) levaram ~1,5 s: 5 s dão três vezes
    // essa folga, e quem estourar fica com o ditado como foi digitado.
    const val TIMEOUT_MS = 5_000L

    const val REASON_DISABLED = "desligado"
    const val REASON_EMPTY = "vazio"
    const val REASON_NOT_CONTIGUOUS = "nao_contiguo"
    const val REASON_PENDING = "pendente"
    const val REASON_TOO_LONG = "muito_longo"
    const val REASON_GUARD = "guard"
    const val REASON_UNCHANGED = "sem_mudanca"
    const val REASON_ERROR = "erro"
    // A trava de destino (P139) recusou a troca na hora de escrever: o campo já não é o mesmo.
    const val REASON_REFUSED = "recusado"
    // O texto que está no campo não confere com o ditado, nem descontando pontuação (P148).
    const val REASON_FIELD_CHANGED = "campo_diferente"
    // A revisão não voltou dentro do teto de espera (P152).
    const val REASON_TIMEOUT = "demorou"
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

    fun request(typed: String, pending: String, contiguous: Boolean, enabled: Boolean): Request = when {
        !enabled -> Request.Skip(REASON_DISABLED)
        // Com pendente, parte do ditado nem chegou ao campo: o que está lá não é o texto todo. Vem
        // antes do texto vazio porque um ditado inteiro pendente não é um ditado vazio.
        pending.isNotBlank() -> Request.Skip(REASON_PENDING)
        typed.isBlank() -> Request.Skip(REASON_EMPTY)
        // Sem contiguidade o que o FlowVoice escreveu não está mais logo antes do cursor (recusa,
        // "Inserir aqui" noutro campo, digitação do usuário no meio): apagar dali apagaria texto do
        // usuário. É a mesma trava da P144, e aqui pesa mais, porque o apagar é do ditado inteiro.
        !contiguous -> Request.Skip(REASON_NOT_CONTIGUOUS)
        typed.length > MAX_CHARS -> Request.Skip(REASON_TOO_LONG)
        else -> Request.Send(typed)
    }

    fun outcome(typed: String, revised: String): Outcome = when {
        // A troca é destrutiva: resposta vazia nunca pode virar um apagar sem escrever de volta.
        revised.isBlank() -> Outcome.Skip(REASON_GUARD)
        // O guard da P132 barra troca de palavra, de número, de negação, aspas novas e as marcas.
        !ProofreadingGuard.accepts(typed, revised) -> Outcome.Skip(REASON_GUARD)
        // Apagar e reescrever o mesmo texto só faria o campo piscar à toa.
        revised == typed -> Outcome.Skip(REASON_UNCHANGED)
        else -> Outcome.Replace(deleteBefore = typed.length, text = revised)
    }
}
