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

    // P155, medido no S26 (2026-09-16 15:28, ditado de ~2 min, 30 janelas, `contextMs=1000`): três
    // emendas entraram dobradas porque a PRIMEIRA palavra do trecho novo é justamente a que o modelo
    // escreveu diferente — o contexto de 1 s começa num ponto qualquer da fala e corta o começo dessa
    // palavra. As palavras seguintes casam exatas.

    // Palavra curta trocada: "do STF" voltou "No STF".
    @Test
    fun aShortWordSwappedAtTheStartOfTheContextIsStillARepetition() {
        val match = TranscriptOverlap.match(
            "Desde o início da crise do STF,",
            "No STF, no começo deste mês, Lula evitava fazer afirmações contundentes.",
            contextDurationMs = 1000
        )

        assertEquals("no começo deste mês, Lula evitava fazer afirmações contundentes.", match.text)
        assertFalse(match.glued)
    }

    // Palavra com o começo cortado: "afirmações" voltou "informações" — o fim ("rmações") foi ouvido.
    @Test
    fun aWordWhoseBeginningWasClippedByTheContextIsStillARepetition() {
        val match = TranscriptOverlap.match(
            "Lula evitava fazer afirmações contundentes.",
            "informações contundentes sobre o caso.",
            contextDurationMs = 1000
        )

        assertEquals("sobre o caso.", match.text)
        assertFalse(match.glued)
    }

    // Palavra curta acrescida, com flexão na última vogal: "julgada" voltou "Em julgado".
    @Test
    fun aShortWordAddedBeforeAnInflectedRepetitionIsStillARepetition() {
        val match = TranscriptOverlap.match(
            "seja defendida e julgada.",
            "Em julgado. Essa é a primeira vez que Lula fala nominalmente dos ministros.",
            contextDurationMs = 1000
        )

        assertEquals("Essa é a primeira vez que Lula fala nominalmente dos ministros.", match.text)
        assertFalse(match.glued)
    }

    // No mesmo ditado a emenda exata funcionou e tem de continuar funcionando.
    @Test
    fun theExactRepetitionMeasuredInTheSameDictationIsStillRemoved() {
        val match = TranscriptOverlap.match(
            "investigação contra Moraes.",
            "Contra Moraes. Um pedido de visita do ministro Flávio Dino.",
            contextDurationMs = 1000
        )

        assertEquals("Um pedido de visita do ministro Flávio Dino.", match.text)
    }

    // Contraprova P155 (não remover): "Nova" não se explica como erro de "a" — não é palavra curta e
    // não divide final nenhum com ela. As três palavras seguintes casam exatas e cabem no contexto, e
    // mesmo assim a frase fica inteira: quem decide é a primeira palavra.
    @Test
    fun aFirstWordThatCannotBeExplainedAsAClippedRepetitionKeepsTheWholeWindow() {
        val match = TranscriptOverlap.match(
            "tomou a dose de dipirona.",
            "Nova dose de dipirona às 18h.",
            contextDurationMs = 1000
        )

        assertEquals("Nova dose de dipirona às 18h.", match.text)
    }

    // Contraprova P155 (não remover): a repetição que o usuário fez está dentro do trecho novo, e
    // "Muito" não é erro explicável de "estava". Antes da P155 o degrau 3 já comia o primeiro "Muito,"
    // — com ou sem contexto —, tomando "cansado." por pedaço de palavra partida no corte.
    @Test
    fun aRepetitionTheSpeakerMadeIsNotEatenByTheClippedOnsetRule() {
        val match = TranscriptOverlap.match(
            "estava muito cansado.",
            "Muito, muito cansado mesmo.",
            contextDurationMs = 1000
        )

        assertEquals("Muito, muito cansado mesmo.", match.text)
        assertEquals(
            "Muito, muito cansado mesmo.",
            TranscriptOverlap.match("estava muito cansado.", "Muito, muito cansado mesmo.").text
        )
    }

    // Contraprova P155 — LIMITAÇÃO ACEITA, não distinção. "o SAMU" + "No SAMU ninguém atendeu." tem a
    // mesma forma do caso medido ("do STF" + "No STF, no começo"): palavra curta com o começo trocado e
    // a palavra seguinte idêntica. Pelo texto não há como saber se o usuário repetiu "no SAMU" ou se
    // o modelo transcreveu o contexto de novo, e a regra remove. O que se perde é só a palavra curta —
    // "SAMU" já está no campo, logo antes. Este teste existe para que a limitação não mude calada.
    @Test
    fun knownLimitationAShortWordPlusARepeatedNameSaidOnPurposeIsRemoved() {
        val match = TranscriptOverlap.match(
            "ligar para o SAMU.",
            "No SAMU ninguém atendeu.",
            contextDurationMs = 1000
        )

        assertEquals("ninguém atendeu.", match.text)
    }

    // A tolerância nova também exige contexto: sem ele os três casos medidos entram inteiros.
    @Test
    fun withoutContextTheClippedOnsetRuleNeverRemovesAnything() {
        assertEquals(
            "No STF, no começo deste mês.",
            TranscriptOverlap.match("a crise do STF,", "No STF, no começo deste mês.").text
        )
        assertEquals(
            "informações contundentes sobre o caso.",
            TranscriptOverlap.match("afirmações contundentes.", "informações contundentes sobre o caso.").text
        )
        assertEquals(
            "Em julgado. Essa é a primeira vez.",
            TranscriptOverlap.match("defendida e julgada.", "Em julgado. Essa é a primeira vez.").text
        )
    }

    // Nunca além do que o contexto comporta: com 200 ms (6 caracteres) "No STF," (7) não cabe.
    @Test
    fun theClippedOnsetRuleNeverRemovesMoreThanTheContextCouldHold() {
        val match = TranscriptOverlap.match("a crise do STF,", "No STF, no começo.", contextDurationMs = 200)

        assertEquals("No STF, no começo.", match.text)
    }

    // A palavra curta trocada precisa de uma palavra de conteúdo (3+ letras) casando depois dela:
    // "do a" contra "no a" é coincidência entre palavras gramaticais, não repetição.
    @Test
    fun aSwappedShortWordFollowedOnlyByShortWordsIsNotEvidence() {
        val match = TranscriptOverlap.match("veio do a", "No a mesma coisa.", contextDurationMs = 1000)

        assertEquals("No a mesma coisa.", match.text)
    }

    // Negação nunca é tolerada como palavra divergente, em degrau nenhum: "ao trabalho" + "Não trabalho
    // mais lá" é fala real, e com o degrau 4 (P150) "já tomou remédio." + "Não tomou remédio." sumia
    // inteiro — "já"/"não" cabia na palavra curta frouxa, apoiada em duas vizinhas idênticas.
    @Test
    fun aNegationIsNeverTakenAsADivergentWordAtTheBoundary() {
        assertEquals(
            "Não trabalho mais lá.",
            TranscriptOverlap.match("voltou ao trabalho.", "Não trabalho mais lá.", contextDurationMs = 1000).text
        )
        assertEquals(
            "Não tomou remédio.",
            TranscriptOverlap.match("já tomou remédio.", "Não tomou remédio.", contextDurationMs = 1000).text
        )
    }

    // O final comum tem de ser maior que um sufixo de derivação: "rapidamente"/"lentamente" dividem
    // só "amente", e são palavras diferentes.
    @Test
    fun wordsSharingOnlyADerivationalSuffixAreNotTheSameWord() {
        val match = TranscriptOverlap.match(
            "respondeu rapidamente depois.",
            "lentamente depois disso.",
            contextDurationMs = 1000
        )

        assertEquals("lentamente depois disso.", match.text)
    }

    // Final comum longo sozinho não basta: sem palavra casando depois dele, a troca de começo pode ser
    // o que o usuário quis dizer. É o caso medido sem o "contundentes" que o sustentava.
    @Test
    fun aSharedLongEndingWithoutAFollowingMatchIsNotEvidence() {
        val match = TranscriptOverlap.match(
            "Lula evitava fazer afirmações.",
            "informações sobre o caso.",
            contextDurationMs = 1000
        )

        assertEquals("informações sobre o caso.", match.text)
    }

    // Flexão com palavra curta acrescida só vale em palavra de 7+ letras: as flexões mais frequentes
    // da fala são curtas ("bonito", "outra", "nova"), e ali a repetição com artigo é fala real.
    @Test
    fun aShortInflectedRepetitionWithAnAddedArticleIsSpeech() {
        val match = TranscriptOverlap.match("a casa ficou bonita.", "O bonito é que ninguém viu.", contextDurationMs = 1000)

        assertEquals("O bonito é que ninguém viu.", match.text)
    }

    // P155, segunda medição no S26 (2026-09-16, `contextMs=1000`): o MESMO ponto do texto falhou em
    // dois ditados. O segundo de contexto começou no meio de "indicações", o modelo ouviu só o fim e
    // completou como outra palavra; "dos ministros" casa exato até o fim do texto anterior. O final
    // comum é "ações" (5 letras), abaixo das 6 que o degrau 5 exige sem mais evidência.

    // 15:28 — a palavra nova é só o final ouvido.
    @Test
    fun aFirstWordThatIsOnlyTheHeardEndingIsStillARepetitionWithStrongEvidenceAfterIt() {
        val match = TranscriptOverlap.match(
            "que não pode ser responsabilizado pelas indicações dos ministros.",
            "Ações dos ministros Alexandre de Moraes.",
            contextDurationMs = 1000
        )

        assertEquals("Alexandre de Moraes.", match.text)
        assertFalse(match.glued)
    }

    // 15:55 — `chars=47`: o final ouvido foi completado com outro começo.
    @Test
    fun aFirstWordCompletedWithAnotherBeginningIsStillARepetitionWithStrongEvidenceAfterIt() {
        val match = TranscriptOverlap.match(
            "que não pode ser responsabilizado pelas indicações dos ministros.",
            "Declarações dos ministros Alexandre de Moraes.",
            contextDurationMs = 1000
        )

        assertEquals("Alexandre de Moraes.", match.text)
        assertFalse(match.glued)
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
