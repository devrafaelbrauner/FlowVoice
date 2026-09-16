package dev.rafaelbrauner.flowvoice.shared.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Deduplicação da emenda entre janelas (P127, endurecida pela P143). Com contexto sobreposto, a
// janela nova começa repetindo as últimas palavras da anterior, e o modelo pode transcrevê-las com
// outro caixa, outros acentos e outra pontuação.
//
// A heurística tem três degraus, do mais seguro ao mais arriscado:
//  1. maior sufixo de `left` igual ao prefixo de `right`, comparado sem acento, sem pontuação e sem
//     caixa;
//  2. o mesmo, ignorando a última palavra de `left` quando ela é um pedaço de palavra partida no
//     corte e a palavra inteira aparece em `right`: o que falta é colado sem espaço;
//  3. o mesmo, sem colar, quando o pedaço não casa com a palavra inteira.
// Os degraus 2 e 3 só valem com ao menos uma palavra de âncora antes do pedaço, o que o contexto
// sobreposto garante. Sem âncora nada é removido.
class TranscriptOverlapTest {
    @Test
    fun theLongestRepeatedRunAtTheBoundaryIsDropped() {
        val match = TranscriptOverlap.match("o médico pediu o exame", "o exame de sangue")

        assertEquals("de sangue", match.text)
        assertFalse(match.glued)
    }

    @Test
    fun theRepeatedRunIsRecognizedWithOtherAccentsCaseAndPunctuation() {
        val match = TranscriptOverlap.match("evoluiu posteriormente com diarreia", "Com diarréia, há três dias")

        assertEquals("há três dias", match.text, "a repetição não é idêntica, mas é a mesma fala")
        assertFalse(match.glued)
    }

    @Test
    fun unrelatedTextKeepsEveryWordOfTheNewWindow() {
        val match = TranscriptOverlap.match("de Grandmont.", "Queda da pressão arterial.")

        assertEquals("Queda da pressão arterial.", match.text)
        assertFalse(match.glued)
    }

    @Test
    fun aWindowFullyRepeatedInTheContextAddsNothing() {
        assertEquals("", TranscriptOverlap.match("pediu o exame", "o exame").text)
    }

    @Test
    fun theFirstWindowKeepsItsWholeTextTrimmed() {
        assertEquals("olá mundo", TranscriptOverlap.match("", "  olá mundo ").text)
        assertEquals("", TranscriptOverlap.match("olá", "   ").text)
    }

    // O ganho do contexto sobreposto: a repetição legítima sobrevive porque a âncora é maior que a
    // própria palavra repetida.
    @Test
    fun aWordTheSpeakerReallyRepeatedSurvivesWhenTheContextAnchorsIt() {
        val match = TranscriptOverlap.match("é muito", "é muito, muito importante")

        assertEquals("muito importante", match.text)
    }

    // Limite conhecido e aceito: sem âncora, a palavra repetida de propósito some. Ela é o preço da
    // deduplicação, que não pode desfazer o que já foi digitado (P139).
    @Test
    fun withoutAnchorAWordRepeatedOnPurposeIsLost() {
        assertEquals("importante", TranscriptOverlap.match("é muito", "muito importante").text)
    }

    @Test
    fun aWordBrokenAtTheCutIsCompletedWithoutASpace() {
        // "di|arreia": a janela anterior acabou no pedaço, e o contexto faz a janela nova transcrever
        // a palavra inteira. O que já foi digitado não se apaga, então só falta colar o resto.
        val match = TranscriptOverlap.match("com episódios de di", "de diarreia")

        assertEquals("arreia", match.text)
        assertTrue(match.glued, "tem de entrar sem espaço, para fechar a palavra")
    }

    @Test
    fun aLongBrokenWordIsAlsoCompleted() {
        val match = TranscriptOverlap.match("quadro de hipertens", "de hipertensão arterial")

        assertEquals("ão arterial", match.text)
        assertTrue(match.glued)
    }

    // Sem âncora o degrau 2 não vale: "a" é artigo, não pedaço de "amostra".
    @Test
    fun aShortWordIsNotGluedIntoTheNextWordWhenNothingAnchorsIt() {
        val match = TranscriptOverlap.match("enviamos para a", "amostra de sangue")

        assertEquals("amostra de sangue", match.text)
        assertFalse(match.glued)
    }

    // Uma letra só nunca cola: seria artigo ou preposição virando começo de palavra.
    @Test
    fun aSingleLetterIsNeverGluedEvenWithAnAnchor() {
        val match = TranscriptOverlap.match("com a", "com amostra")

        assertEquals("amostra", match.text)
        assertFalse(match.glued, "\"com a amostra\" é mais provável que \"com amostra\"")
    }

    // Degrau 3: o pedaço não casa com a palavra inteira, então a âncora é removida e o pedaço fica.
    // Repetir é feio, mas trocar a palavra seria erro.
    @Test
    fun aFragmentThatDoesNotMatchTheWholeWordStillDropsTheAnchor() {
        val match = TranscriptOverlap.match("episódios de xyz", "de diarreia")

        assertEquals("diarreia", match.text)
        assertFalse(match.glued)
    }

    // P150: no S26 (2026-09-16, `openai/gpt-transcribe`) o contexto de 1 s da P143 voltou transcrito
    // com outras palavras — a janela 0 disse "está muito bonito" e a janela 1 disse "Tá muito bonito"
    // pelo mesmo áudio. A comparação por letras não reconheceu e "muito bonito" entrou duas vezes.
    @Test
    fun theContextTranscribedWithOtherWordsIsStillRecognizedAsARepetition() {
        val match = TranscriptOverlap.match(
            "Hoje o dia está muito bonito.",
            "Tá muito bonito, por isso iremos para a praia pela manhã.",
            contextDurationMs = 1000
        )

        assertEquals("por isso iremos para a praia pela manhã.", match.text)
        assertFalse(match.glued)
    }

    // A janela sem contexto não repetiu áudio nenhum: o que parece repetição ali é fala de verdade, e
    // o casamento aproximado nem é tentado.
    @Test
    fun withoutContextTheApproximateRuleNeverRemovesAnything() {
        val match = TranscriptOverlap.match(
            "Hoje o dia está muito bonito.",
            "Tá muito bonito, por isso iremos para a praia pela manhã."
        )

        assertEquals("Tá muito bonito, por isso iremos para a praia pela manhã.", match.text)
    }

    // Nunca se apaga mais do que o contexto poderia conter: com 200 ms de contexto não cabem as três
    // palavras, então nada sai. Repetir é reversível pelo usuário; apagar fala, não.
    @Test
    fun theApproximateRuleNeverRemovesMoreThanTheContextCouldHold() {
        val match = TranscriptOverlap.match(
            "Hoje o dia está muito bonito.",
            "Tá muito bonito, por isso iremos para a praia pela manhã.",
            contextDurationMs = 200
        )

        assertEquals("Tá muito bonito, por isso iremos para a praia pela manhã.", match.text)
    }

    // Uma palavra curta e diferente é palpite, não prova: sem duas palavras casando atrás dela, a
    // frase nova entra inteira.
    @Test
    fun aSingleDivergentShortWordIsNotEvidenceEnoughToRemoveAnything() {
        val match = TranscriptOverlap.match("Hoje o dia está", "Tá chovendo muito.", contextDurationMs = 1000)

        assertEquals("Tá chovendo muito.", match.text)
    }

    // A regra só olha o começo da janela nova contra o fim da anterior. O que o usuário repetiu no
    // meio da própria fala não é emenda e não é tocado.
    @Test
    fun aRepetitionInsideTheNewWindowIsNotTouched() {
        val match = TranscriptOverlap.match("hoje o dia está", "chovia, tá muito frio", contextDurationMs = 1000)

        assertEquals("chovia, tá muito frio", match.text)
    }

    // Um trecho só de pontuação casaria com qualquer outro, então não conta como repetição: o traço
    // repetido sobra, o que é preferível a apagar palavra de verdade por causa de um travessão.
    @Test
    fun punctuationOnlyTokensDoNotAnchorAnOverlap() {
        val match = TranscriptOverlap.match("fim da frase —", "— começo da outra")

        assertEquals("— começo da outra", match.text)
        assertFalse(match.glued)
    }
}
