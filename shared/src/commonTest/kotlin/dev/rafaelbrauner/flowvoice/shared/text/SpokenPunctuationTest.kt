package dev.rafaelbrauner.flowvoice.shared.text

import dev.rafaelbrauner.flowvoice.shared.pipeline.DirectInsertionPlan
import dev.rafaelbrauner.flowvoice.shared.pipeline.DirectInsertionPlanner
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlin.test.Test
import kotlin.test.assertEquals

// Casos portados do SpokenPunctuationTest do Intelligent Keyboard (texto como o modelo devolveu cada
// trecho no aparelho, 11/set), mais os do FlowVoice.
class SpokenPunctuationTest {
    private fun p(text: String) = SpokenPunctuation.apply(text)
    private fun segment(text: String) = SpokenPunctuation.applyToSegment(text)

    // S26, 2026-09-28 (captura do usuário no Samsung Notes): o comando saía escrito por extenso.
    @Test
    fun theDictationFromTheUsersScreenshotGetsTheFullStopAndTheLineBreak() {
        assertEquals(
            "Testando um dois três.\nAmanhã vamos à praia",
            segment("Testando um dois três ponto final Nova Linha Amanhã vamos à praia")
        )
    }

    @Test
    fun listItemsStartALineWithADashAndACapital() {
        assertEquals(
            "Prescrição:\n- Dipirona de seis em seis horas\n- Omeprazol em jejum",
            p("Prescrição dois pontos novo item dipirona de seis em seis horas próximo item omeprazol em jejum")
        )
        assertEquals("\n- Soro fisiológico", segment("Próximo item, soro fisiológico"))
    }

    // Uma vírgula dita sozinha depois da pausa: o ponto que o modelo pôs no trecho anterior sai pela
    // regra que o planejador já tem (P144), e a vírgula entra colada.
    @Test
    fun aCommaSaidAloneAfterAPauseReplacesTheModelsFullStop() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, segment("O paciente está estável.")), ok(1, segment("Vírgula.")), ok(2, segment("sem febre.")))
        )

        assertEquals("O paciente está estável, sem febre.", step.plan.transcript)
    }

    @Test
    fun colonWithTheFullStopTheModelAdded() {
        assertEquals("Observe: O texto começa", p("Observe dois pontos. O texto começa"))
    }

    @Test
    fun questionMarkHeardAsIntervogacaoAloneInASegment() {
        assertEquals("?", p(" intervogação."))
        assertEquals("Ele fica trocando muito enquanto você fala?", p("Ele fica trocando muito enquanto você fala interrogação"))
    }

    @Test
    fun theModelsPunctuationAroundTheCommandIsDropped() {
        assertEquals("Você fala?", p("Você fala, interrogação."))
        assertEquals("Tudo bem, e você?", p("Tudo bem vírgula e você interrogação"))
    }

    @Test
    fun pontoAloneIsStillText() {
        assertEquals("Marquei um ponto no jogo.", p("Marquei um ponto no jogo."))
        assertEquals("Fica no ponto de ônibus.", p("Fica no ponto de ônibus."))
        assertEquals("Do meu ponto de vista", p("Do meu ponto de vista"))
    }

    @Test
    fun sentenceEndsCapitalizeTheNextWord() {
        assertEquals("Fim. Começa outra frase", p("Fim ponto final começa outra frase"))
        assertEquals("Que bom! Vamos", p("Que bom exclamação vamos"))
        assertEquals("Chegou? Ótimo", p("Chegou ponto de interrogação ótimo"))
    }

    @Test
    fun semicolonIsNotAFullStopFollowedByAComma() {
        assertEquals("Uso; aqui", p("Uso ponto e vírgula aqui"))
    }

    @Test
    fun lineAndParagraphBreaks() {
        assertEquals("Primeira\nSegunda", p("Primeira nova linha segunda"))
        assertEquals("Fim da frase.\nPróxima", p("Fim da frase. Nova linha. Próxima"))
        assertEquals("Título\n\nTexto", p("Título novo parágrafo texto"))
    }

    @Test
    fun quotesAndParenthesesStickToTheRightSide() {
        assertEquals("Ela disse \"bom dia\" e saiu", p("Ela disse abre aspas bom dia fecha aspas e saiu"))
        assertEquals("Dor (leve) à noite", p("Dor abre parênteses leve fecha parêntese à noite"))
    }

    @Test
    fun withoutAccentsAndInCapitalsAlsoCount() {
        assertEquals("Sim, claro", p("Sim VIRGULA claro"))
        assertEquals("E então...", p("E então reticências"))
    }

    @Test
    fun similarButDifferentWordIsNotACommand() {
        assertEquals("Faltaram vírgulas no texto", p("Faltaram vírgulas no texto"))
        assertEquals("Muitas interrogações", p("Muitas interrogações"))
    }

    @Test
    fun punctuationBetweenTheWordsOfACommandSeparatesThem() {
        assertEquals("Chegou no ponto, final da linha", p("Chegou no ponto, final da linha"))
    }

    @Test
    fun edgeSpacesAreKept() {
        assertEquals(" tudo bem? ", p(" tudo bem interrogação "))
        assertEquals(" texto comum ", p(" texto comum "))
    }

    @Test
    fun doisPontosThatIsANumberStaysText() {
        assertEquals("Foram dados dois pontos de sutura", p("Foram dados dois pontos de sutura"))
        assertEquals("Subiu dois pontos percentuais", p("Subiu dois pontos percentuais"))
        assertEquals("Ganhou mais dois pontos", p("Ganhou mais dois pontos"))
    }

    @Test
    fun commaBetweenDigitsIsDecimalAndSpelledOutStaysAsIs() {
        assertEquals("Temperatura 37,5 graus", p("Temperatura 37 vírgula 5 graus"))
        assertEquals("Zero vírgula cinco miligramas", p("Zero vírgula cinco miligramas"))
    }

    @Test
    fun theSignsNameAfterAnArticleOrPrepositionIsTalkAboutTheSign() {
        assertEquals("Faltou o sinal de interrogação", p("Faltou o sinal de interrogação"))
        assertEquals("Coloca uma vírgula aqui", p("Coloca uma vírgula aqui"))
        assertEquals("Desce no ponto final", p("Desce no ponto final"))
        assertEquals("E como?", p("E como interrogação"))
    }

    @Test
    fun twoPontosWrittenAsDigitIsAlsoACommand() {
        assertEquals("Observe: O texto começa", p("Observe 2 pontos. O texto começa"))
        assertEquals(":", p(" 2 pontos."))
    }

    @Test
    fun twoPontosThatIsANumberStaysText() {
        assertEquals("Foram dados 2 pontos de sutura", p("Foram dados 2 pontos de sutura"))
        assertEquals("Subiu 2 pontos percentuais", p("Subiu 2 pontos percentuais"))
        assertEquals("Ganhou mais 2 pontos", p("Ganhou mais 2 pontos"))
        assertEquals("Escore de 2 pontos", p("Escore de 2 pontos"))
    }

    @Test
    fun questionMarkWhereTheModelAlreadyTurnedPontoIntoADot() {
        assertEquals("Está tudo bem?", p("Está tudo bem.de interrogação"))
        assertEquals("?", p(".de interrogação"))
        assertEquals("!", p(" .De exclamação."))
        assertEquals("Chegou? Ótimo", p("Chegou. de interrogação ótimo"))
    }

    @Test
    fun fullStopFollowedByDeStaysText() {
        assertEquals("Fim. De manhã cedo", p("Fim. De manhã cedo"))
        assertEquals("Fim. De novo", p("Fim. De novo"))
    }

    @Test
    fun aSegmentThatIsOnlyAMisheardCommand() {
        assertEquals("\n", segment(" Nova Lí"))
        assertEquals("\n", segment("Nova lin"))
        assertEquals("\n", segment("Nova linia."))
        assertEquals("\n", segment("nova línea"))
        assertEquals(".", segment(" O latino final."))
        assertEquals(".", segment("Ponto finau."))
        assertEquals(".", segment("ponto fina"))
    }

    @Test
    fun aShortSegmentThatIsSpeechStaysText() {
        assertEquals("Nova Lima.", segment("Nova Lima."))
        assertEquals("Resultado final.", segment("Resultado final."))
        assertEquals("Versão final.", segment("Versão final."))
        assertEquals("A prova final.", segment("A prova final."))
        assertEquals("Final.", segment("Final."))
        assertEquals("Nova Lí e mais coisa", segment("Nova Lí e mais coisa"))
    }

    @Test
    fun outsideASingleSegmentTheMisheardCommandIsNotRecognized() {
        assertEquals(" Nova Lí", p(" Nova Lí"))
        assertEquals("O latino final.", p("O latino final."))
    }

    @Test
    fun textWithoutCommandsComesBackIdentical() {
        val text = "O paciente está com muita dor abdominal."
        assertEquals(text, p(text))
    }

    private fun ok(window: Int, text: String) =
        TranscriptionSegment(windowIndex = window, status = TranscriptionSegment.Status.Ok, text = text)
}
