package dev.rafaelbrauner.flowvoice.shared.pipeline

// O campo é conferido antes e depois de cada pedaço do ditado direto (P142). A
// `AccessibilityService.InputMethod.AccessibilityInputConnection` não expõe `finishComposingText`,
// então não há como encerrar a composição do teclado e garantir que o apagar e o escrever saiam como
// pedidos: no S26 (2026-09-16 10:29 e 11:05) a revisão final mediu `drift=1`, um caractere a mais no
// campo do que o app achava ter escrito, e o espaço da emenda já sumiu depois de um ponto (P143).
// Aqui não se adivinha a causa: lê-se o campo (`getSurroundingText`) antes e depois de escrever e
// compara-se com o que foi pedido. Nada nesta decisão fala com o campo — quem chama é que escreve.
object DirectFieldWrite {
    // Âncora de texto anterior que a leitura leva junto, além do que será escrito e apagado. Serve
    // para reconhecer onde termina o texto que já estava no campo e começa o que acabamos de
    // escrever; 24 caracteres são ~4 palavras, o bastante para não casar por acaso.
    const val ANCHOR = 24

    // Folga sobre o que foi escrito e apagado, dentro da qual uma divergência ainda é considerada
    // obra nossa. Acima disso a diferença é grande demais para ser um espaço ou um ponto, e o campo
    // fica como está.
    const val SLACK = 8

    // O campo não confirma o caractere que o app quer apagar.
    const val REASON_UNCONFIRMED = "nao_confirmado"

    // O fim do campo não bate nem com o que estava lá antes nem com o que foi escrito: alguém mexeu.
    const val REASON_UNANCHORED = "sem_ancora"

    // Não há leitura do campo (desktop, API antiga, campo ilegível).
    const val REASON_UNREADABLE = "sem_leitura"

    sealed interface Verdict {
        // O campo ficou como o app pediu.
        data object Ok : Verdict

        // Não deu para conferir; vale a conta do app, como antes da P142.
        data class Unknown(val reason: String) : Verdict

        // O fim do campo não é o que foi pedido, mas a divergência cabe no que acabamos de escrever:
        // apagar `deleteBefore` e escrever `text` **num passo só** devolve o campo ao que foi pedido.
        data class Repair(val deleteBefore: Int, val text: String) : Verdict

        // Diverge além do que escrevemos: refazer apagaria texto que não é nosso. Nada é tocado.
        data class Mismatch(val reason: String) : Verdict
    }

    fun readLimit(deleteBefore: Int, text: String): Int = text.length + deleteBefore + ANCHOR

    // Quanto apagar de verdade. O app quer tirar o ponto que ele mesmo escreveu (P144); se o campo
    // não termina com esse caractere, ele já não está onde o app pensa, e apagar dali comeria texto
    // do usuário. Sem leitura do campo vale a conta do app, como antes.
    fun erase(before: String?, typed: String, deleteBefore: Int): Int {
        if (deleteBefore <= 0) return 0
        if (before == null) return deleteBefore
        val wanted = typed.takeLast(deleteBefore)
        if (wanted.length < deleteBefore) return 0
        return if (before.endsWith(wanted)) deleteBefore else 0
    }

    fun verdict(before: String?, after: String?, erased: Int, written: String): Verdict {
        if (before == null || after == null) return Verdict.Unknown(REASON_UNREADABLE)
        if (written.isEmpty()) return Verdict.Unknown(REASON_UNREADABLE)
        val head = before.dropLast(erased)
        val expected = head + written
        // As duas leituras têm o mesmo teto, e depois de escrever o campo cresceu: basta uma ser
        // sufixo da outra para o que foi pedido estar lá.
        if (expected.endsWith(after) || after.endsWith(expected)) return Verdict.Ok
        val reach = written.length + erased + SLACK
        // Menor pedaço do fim do campo que não estava lá antes de escrever: é o que o nosso write
        // deixou. O que vem antes dele tem de bater com o campo de antes, senão não é âncora.
        for (k in 0..minOf(reach, after.length)) {
            val anchor = after.dropLast(k)
            if (!head.endsWith(anchor)) continue
            if (after.takeLast(k) == written) return Verdict.Ok
            // Sem nenhuma âncora do texto que já estava no campo não dá para saber onde começa o
            // nosso pedaço, e apagar dali comeria o que pode não ser do FlowVoice.
            if (anchor.isEmpty()) return Verdict.Mismatch(REASON_UNANCHORED)
            return Verdict.Repair(deleteBefore = k, text = written)
        }
        return Verdict.Mismatch(REASON_UNANCHORED)
    }
}
