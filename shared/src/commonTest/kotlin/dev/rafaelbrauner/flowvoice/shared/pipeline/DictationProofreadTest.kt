package dev.rafaelbrauner.flowvoice.shared.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Revisão final do ditado direto (P147): decidir se vale pedir a revisão e, com a resposta na mão,
// o que apagar e o que escrever. Nada aqui fala com a rede nem com o campo.
class DictationProofreadTest {
    @Test
    fun aFinishedDictationIsSentForProofreadingExactlyAsItWasTyped() {
        val request = DictationProofread.request(
            typed = "Hoje o dia está muito bonito. Por isso iremos para a praia.",
            pending = "",
            contiguous = true,
            enabled = true
        )

        val send = assertIs<DictationProofread.Request.Send>(request)
        assertEquals("Hoje o dia está muito bonito. Por isso iremos para a praia.", send.text)
    }

    // O caso do S26 (2026-09-16 09:29): o modelo fecha cada janela com ponto e abre a seguinte com
    // maiúscula; o texto final da passada troca tudo o que o app digitou.
    @Test
    fun aFinalTextReplacesEverythingTheAppTyped() {
        val typed = "Hoje o dia está muito bonito. Por isso iremos para a praia. Pois lá tem uma água de coco bem gelada."
        val final = "Hoje o dia está muito bonito, por isso iremos para a praia, pois lá tem uma água de coco bem gelada."

        val outcome = DictationProofread.replacement(typed, final)

        val replace = assertIs<DictationProofread.Outcome.Replace>(outcome)
        assertEquals(typed.length, replace.deleteBefore)
        assertEquals(final, replace.text)
    }

    // Texto final igual ao ditado: apagar e reescrever o mesmo texto só faria o campo piscar.
    @Test
    fun aFinalTextIdenticalToTheDictationErasesAndWritesNothing() {
        val typed = "Bom dia, Marina."

        val outcome = DictationProofread.replacement(typed, typed)

        val skip = assertIs<DictationProofread.Outcome.Skip>(outcome)
        assertEquals(DictationProofread.REASON_UNCHANGED, skip.reason)
    }

    @Test
    fun withAiProofreadingOffTheDictationIsLeftAsItIs() {
        val request = DictationProofread.request(
            typed = "Hoje o dia está muito bonito.",
            pending = "",
            contiguous = true,
            enabled = false
        )

        val skip = assertIs<DictationProofread.Request.Skip>(request)
        assertEquals(DictationProofread.REASON_DISABLED, skip.reason)
    }

    // Depois de uma recusa ou de "Inserir aqui" noutro campo, o que o FlowVoice escreveu já não está
    // logo antes do cursor: apagar dali seria apagar texto do usuário.
    @Test
    fun aDictationNoLongerRightBeforeTheCursorIsNeverReplaced() {
        val request = DictationProofread.request(
            typed = "Hoje o dia está muito bonito.",
            pending = "",
            contiguous = false,
            enabled = true
        )

        val skip = assertIs<DictationProofread.Request.Skip>(request)
        assertEquals(DictationProofread.REASON_NOT_CONTIGUOUS, skip.reason)
    }

    // Com texto pendente a sessão está pausada: parte do ditado não está no campo, e o que está não
    // é o ditado inteiro.
    @Test
    fun aDictationWithTextStillPendingIsNotProofread() {
        val request = DictationProofread.request(
            typed = "Hoje o dia está muito bonito.",
            pending = "Por isso iremos para a praia.",
            contiguous = true,
            enabled = true
        )

        val skip = assertIs<DictationProofread.Request.Skip>(request)
        assertEquals(DictationProofread.REASON_PENDING, skip.reason)
    }

    @Test
    fun anEmptyDictationNeverReachesTheModel() {
        val request = DictationProofread.request(typed = "   ", pending = "", contiguous = true, enabled = true)

        val skip = assertIs<DictationProofread.Request.Skip>(request)
        assertEquals(DictationProofread.REASON_EMPTY, skip.reason)
    }

    // Acima do teto, apagar e reescrever tudo levaria o campo inteiro junto num único passo; o
    // ditado fica como está.
    @Test
    fun aDictationLongerThanTheCapIsLeftAsItIs() {
        val request = DictationProofread.request(
            typed = "a".repeat(DictationProofread.MAX_CHARS + 1),
            pending = "",
            contiguous = true,
            enabled = true
        )

        val skip = assertIs<DictationProofread.Request.Skip>(request)
        assertEquals(DictationProofread.REASON_TOO_LONG, skip.reason)
    }

    @Test
    fun aDictationExactlyAtTheCapIsStillProofread() {
        val typed = "a".repeat(DictationProofread.MAX_CHARS)

        val request = DictationProofread.request(typed = typed, pending = "", contiguous = true, enabled = true)

        assertIs<DictationProofread.Request.Send>(request)
    }

    // A resposta vazia não pode virar um apagar sem escrever nada.
    @Test
    fun anEmptyFinalTextErasesNothing() {
        val outcome = DictationProofread.replacement(typed = "Bom dia, Marina.", final = "   ")

        val skip = assertIs<DictationProofread.Outcome.Skip>(outcome)
        assertEquals(DictationProofread.REASON_EMPTY, skip.reason)
    }
}
