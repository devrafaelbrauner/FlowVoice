package dev.rafaelbrauner.flowvoice.shared.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DirectFieldWriteTest {
    // No S26 (2026-09-16 14:47) a leitura feita logo depois de escrever voltou com o campo **de
    // antes**: `dictation_write_mismatch window=1 campo=0 chars=36 acao=falhou`. O texto tinha
    // entrado — o ditado terminou com `inserted=true` e a revisão final apagou o que havia escrito —,
    // e a rota atômica só não escreveu o pedaço uma segunda vez porque foi recusada. Campo que não
    // mudou em nada é leitura velha, não escrita perdida: não se refaz.
    @Test
    fun aFieldReadThatCameBackUnchangedIsNotTakenForAFailedWrite() {
        val before = "Paciente com dispneia aos esforços."

        val verdict = DirectFieldWrite.verdict(
            before = before,
            after = before,
            erased = 0,
            written = " Segue internada."
        )

        assertIs<DirectFieldWrite.Verdict.Unknown>(verdict)
        assertEquals(DirectFieldWrite.REASON_STALE, verdict.reason)
    }

    // O caso medido no S26 (2026-09-16 14:55), com o diagnóstico ligado:
    // `direct_write_audit window=2 antes=O exame de sangue. depois=O exame de sangue`.
    // O apagar da P144 já estava no campo, mas o texto escrito ainda não — a leitura pegou o editor
    // no meio da escrita. Nada do nosso pedaço aparece no campo, e refazer dali escreveria o trecho
    // duas vezes. O ditado terminou certo ("O exame de sangue mostrou leucocitose importante.").
    @Test
    fun aFieldWithoutAnyOfWhatWasJustWrittenIsAStaleReadAndNotARewrite() {
        val verdict = DirectFieldWrite.verdict(
            before = "O exame de sangue.",
            after = "O exame de sangue",
            erased = 1,
            written = " mostrou leucocitose importante."
        )

        assertIs<DirectFieldWrite.Verdict.Unknown>(verdict)
        assertEquals(DirectFieldWrite.REASON_STALE, verdict.reason)
    }

    // Mesma leitura velha, agora com o apagar da P144 no meio: o campo volta idêntico ao de antes.
    @Test
    fun aFieldReadThatCameBackUnchangedAfterAnEraseIsNotRedoneEither() {
        val before = "O exame de sangue."

        val verdict = DirectFieldWrite.verdict(
            before = before,
            after = before,
            erased = 1,
            written = " mostrou leucocitose."
        )

        assertIs<DirectFieldWrite.Verdict.Unknown>(verdict)
        assertEquals(DirectFieldWrite.REASON_STALE, verdict.reason)
    }

    @Test
    fun theFieldEndingWithWhatTheAppWroteErasesWhatWasAskedFor() {
        val typed = "O exame de sangue."
        val field = "Anotação minha: O exame de sangue."

        assertEquals(1, DirectFieldWrite.erase(before = field, typed = typed, deleteBefore = 1))
    }

    // Sem leitura do campo (desktop, API antiga, campo ilegível) vale a conta do próprio app, como antes.
    @Test
    fun withoutReadingTheFieldTheAppCountIsWhatCounts() {
        assertEquals(1, DirectFieldWrite.erase(before = null, typed = "O exame de sangue.", deleteBefore = 1))
    }

    // O ponto que o app quer tirar não está no campo: apagar dali comeria um caractere que não é nosso.
    @Test
    fun aFieldThatDoesNotEndWithTheCharacterTheAppWantsToEraseErasesNothing() {
        assertEquals(0, DirectFieldWrite.erase(before = "O exame de sangue", typed = "O exame de sangue.", deleteBefore = 1))
    }

    @Test
    fun aFieldThatEndedUpAsAskedIsApproved() {
        val before = "O exame de sangue."
        val after = "O exame de sangue mostrou leucocitose."

        assertIs<DirectFieldWrite.Verdict.Ok>(
            DirectFieldWrite.verdict(before = before, after = after, erased = 1, written = " mostrou leucocitose.")
        )
    }

    // A leitura tem teto: o campo devolve menos do que o esperado e ainda assim está certo.
    @Test
    fun aFieldReadShorterThanExpectedIsStillApproved() {
        assertIs<DirectFieldWrite.Verdict.Ok>(
            DirectFieldWrite.verdict(
                before = "de sangue.",
                after = "sangue mostrou leucocitose.",
                erased = 1,
                written = " mostrou leucocitose."
            )
        )
    }

    // O caso medido no S26 (drift=1): o apagar não chegou ao campo e o ponto ficou no meio da frase.
    // Refazer é apagar o ponto e o pedaço recém-escrito e escrever o pedaço de novo, num passo só.
    @Test
    fun aFieldThatSwallowedTheEraseIsRedoneWithTheLeftoverCharacter() {
        val written = " mostrou leucocitose."
        val verdict = DirectFieldWrite.verdict(
            before = "O exame de sangue.",
            after = "O exame de sangue. mostrou leucocitose.",
            erased = 1,
            written = written
        )

        assertEquals(DirectFieldWrite.Verdict.Repair(deleteBefore = written.length + 1, text = written), verdict)
    }

    // O espaço da emenda some depois do ponto (P143, "de Grandmont.Queda"): o campo ficou com um
    // caractere a menos do que foi mandado.
    @Test
    fun aFieldThatSwallowedTheSeparatorSpaceIsRedoneWithTheSpace() {
        val written = " Queda da pressão arterial."
        val verdict = DirectFieldWrite.verdict(
            before = "de Grandmont.",
            after = "de Grandmont.Queda da pressão arterial.",
            erased = 0,
            written = written
        )

        assertEquals(DirectFieldWrite.Verdict.Repair(deleteBefore = written.length - 1, text = written), verdict)
    }

    // Texto do usuário no meio: a divergência passa do que o FlowVoice acabou de escrever, e refazer
    // apagaria o que não é nosso. Nada é tocado.
    @Test
    fun aFieldThatGrewBeyondWhatWasWrittenIsLeftAlone() {
        val verdict = DirectFieldWrite.verdict(
            before = "O exame de sangue.",
            after = "O exame de sangue. Anotação minha no meio. mostrou leucocitose.",
            erased = 1,
            written = " mostrou leucocitose."
        )

        assertIs<DirectFieldWrite.Verdict.Mismatch>(verdict)
    }

    // Campo sem nada antes do nosso pedaço: não há âncora que diga onde ele começa, e refazer dali
    // apagaria o que pode não ser do FlowVoice.
    @Test
    fun aFieldWithoutAnyAnchorBeforeTheWriteIsLeftAlone() {
        val written = "Hoje o dia está bonito."
        val verdict = DirectFieldWrite.verdict(before = "", after = "$written.", erased = 0, written = written)

        assertIs<DirectFieldWrite.Verdict.Mismatch>(verdict)
    }

    @Test
    fun withoutReadingTheFieldThereIsNothingToCheck() {
        assertIs<DirectFieldWrite.Verdict.Unknown>(
            DirectFieldWrite.verdict(before = "O exame.", after = null, erased = 0, written = " mostrou.")
        )
        assertIs<DirectFieldWrite.Verdict.Unknown>(
            DirectFieldWrite.verdict(before = null, after = "O exame.", erased = 0, written = " mostrou.")
        )
    }

    // A leitura precisa cobrir o que foi escrito, o que foi apagado e uma âncora do texto anterior.
    @Test
    fun theReadLimitCoversTheWholeJunction() {
        val text = " mostrou leucocitose."

        assertEquals(text.length + 1 + DirectFieldWrite.ANCHOR, DirectFieldWrite.readLimit(deleteBefore = 1, text = text))
    }
}
