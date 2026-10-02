package dev.rafaelbrauner.flowvoice.shared.dictionary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalDictionaryTest {
    @Test
    fun applyReplacesCaseInsensitiveWithApprovedSpelling() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.approve("OpenRouter")
        assertEquals(
            "Use OpenRouter hoje",
            dictionary.apply("Use openrouter hoje")
        )
    }

    // P145, medido no S26 em 2026-09-16 com `openai/gpt-transcribe`: três termos do usuário saíram
    // errados e o vocabulário palavra a palavra não alcançava nenhum deles, porque em dois casos a
    // fronteira entre as palavras mudou.
    @Test
    fun applyFixesTheThreeTermsMeasuredOnTheDevice() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.approve("dispneia")
        dictionary.approve("na praia")
        // A medição não guardou o sobrenome verdadeiro; o que o aparelho escreveu foi "Grandmont".
        dictionary.approve("Grandemont")

        // Uma palavra virou duas.
        assertEquals(
            "Paciente com dispneia aos esforços",
            dictionary.apply("Paciente com de Espinéia aos esforços")
        )
        // Duas palavras viraram uma.
        assertEquals(
            "Por isso iremos na praia pela manhã",
            dictionary.apply("Por isso iremos napraj pela manhã")
        )
        // Nome próprio transcrito sem contexto.
        assertEquals(
            "Segundo doutor Grandemont",
            dictionary.apply("Segundo doutor Grandmont")
        )
    }

    // O outro lado da P145: o que a regra NÃO pode pegar. Trocar consoante é trocar a palavra, e num
    // ditado clínico isso inverteria o quadro; a última vogal é flexão, não erro de transcrição.
    @Test
    fun applyNeverRewritesSpeechThatMerelySoundsLikeTheTerm() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.approve("hipotensão")
        dictionary.approve("na praia")
        dictionary.approve("comprimida")

        assertEquals(
            "Evoluiu com hipertensão arterial",
            dictionary.apply("Evoluiu com hipertensão arterial")
        )
        assertEquals("Ficamos na praça central", dictionary.apply("Ficamos na praça central"))
        assertEquals("Tomou o comprimido inteiro", dictionary.apply("Tomou o comprimido inteiro"))
    }

    @Test
    fun applyNeverTouchesNumbersDosesOrUnits() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.approve("Dipirona")
        dictionary.approve("500 ml")

        assertEquals(
            "Tomar Dipirona 500 mg de 8 em 8 horas",
            dictionary.apply("Tomar dipirona 500 mg de 8 em 8 horas")
        )
        assertEquals("Prescrevi 50 ml agora", dictionary.apply("Prescrevi 50 ml agora"))
    }

    @Test
    fun emptyDictionaryChangesNothing() {
        val dictionary = InMemoryPersonalDictionary()
        val text = "Paciente com de Espinéia aos esforços, napraj"

        assertEquals(text, dictionary.apply(text))
    }

    @Test
    fun suggestIgnoresShortStopwordsAndKnownTerms() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.approve("sangue")
        val suggestions = dictionary.suggestFrom("O médico pediu o exame de sangue para amanhã")
        assertTrue(suggestions.any { it.equals("médico", ignoreCase = true) })
        assertTrue(suggestions.none { it.equals("sangue", ignoreCase = true) })
        assertTrue(suggestions.none { it.length < 4 })
    }

    @Test
    fun rejectRemovesApprovedAndPending() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.approve("Lactato")
        dictionary.suggestFrom("Segundo lactato pedido")
        dictionary.reject("lactato")
        assertTrue(dictionary.approved().isEmpty())
        assertEquals("Segundo lactato pedido", dictionary.apply("Segundo lactato pedido"))
    }

    // N9 (revisão de código): as sugestões pendentes cresciam sem limite a cada ditado.
    @Test
    fun pendingSuggestionsAreCappedDroppingTheOldest() {
        val dictionary = InMemoryPersonalDictionary()
        val cap = InMemoryPersonalDictionary.MAX_PENDING_SUGGESTIONS
        repeat(cap + 20) { dictionary.suggestFrom("termo${it}x") }

        val pending = dictionary.pending().map { it.surface }
        assertEquals(cap, pending.size)
        assertEquals("termo20x", pending.first())
        assertEquals("termo${cap + 19}x", pending.last())
    }

    // Item 1 da revisão do motor: o que o usuário ensinou explicitamente vence o termo aprovado e o
    // casamento por som. Sem a regra, "caza" ficava como o termo exato aprovado, e "napraj" era
    // reescrito inteiro pelo termo "na praia" (P145); com a regra, a palavra ensinada decide.
    @Test
    fun correctionRuleWinsOverApprovedTermAndSound() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.approve("caza")
        dictionary.learnCorrection("caza", "casa")
        assertEquals("casa", dictionary.apply("caza"))

        val semRegra = InMemoryPersonalDictionary()
        semRegra.approve("na praia")
        assertEquals("iremos na praia pela manhã", semRegra.apply("iremos napraj pela manhã"))

        val comRegra = InMemoryPersonalDictionary()
        comRegra.approve("na praia")
        comRegra.learnCorrection("napraj", "napraia")
        assertEquals("iremos napraia pela manhã", comRegra.apply("iremos napraj pela manhã"))
    }

    // A caixa da palavra casada é do texto: "Caza" em início de frase vira "Casa", não "casa".
    @Test
    fun correctionRuleKeepsTheCapitalOfTheMatchedWord() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.learnCorrection("caza", "casa")
        assertEquals("Casa alta", dictionary.apply("Caza alta"))
        assertEquals("a casa", dictionary.apply("a caza"))
    }

    // Mesma proteção da janela de som: token com dígito é número, dose ou unidade, e a regra não o
    // toca — só a palavra inteira sem dígito é trocada.
    @Test
    fun correctionRuleNeverTouchesWordsWithDigits() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.learnCorrection("caza", "casa")
        assertEquals(
            "caza1 e 2caza ficam; só casa muda",
            dictionary.apply("caza1 e 2caza ficam; só caza muda")
        )
    }

    @Test
    fun forgetCorrectionStopsReplacing() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.learnCorrection("caza", "casa")
        assertEquals("minha casa", dictionary.apply("minha caza"))
        // A chave da regra é sem caixa, então esquecer "CAZA" acha a mesma regra.
        dictionary.forgetCorrection("CAZA")
        assertEquals("minha caza", dictionary.apply("minha caza"))
        assertTrue(dictionary.corrections().isEmpty())
    }

    // Esquecer a regra não rebaixa o lado certo: "casa" segue aprovado no vocabulário.
    @Test
    fun learningCorrectionApprovesRightAndForgettingKeepsItApproved() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.learnCorrection("caza", "casa")
        assertTrue(dictionary.approved().any { it.surface == "casa" })
        assertEquals(listOf(CorrectionPair("caza", "casa")), dictionary.corrections())

        dictionary.forgetCorrection("caza")

        assertTrue(dictionary.corrections().isEmpty())
        assertTrue(dictionary.approved().any { it.surface == "casa" })
    }

    // Cap de 200 regras em FIFO: aprendendo além do teto, sai a regra mais antiga. Os nomes vão só
    // de letras porque a regra exige uma palavra sem dígito.
    @Test
    fun correctionRulesAreCappedDroppingTheOldest() {
        val dictionary = InMemoryPersonalDictionary()
        val cap = InMemoryPersonalDictionary.MAX_CORRECTION_RULES
        repeat(cap + 20) { index ->
            val wrong = ruleName(index)
            dictionary.learnCorrection(wrong, "certa" + wrong.removePrefix("erro"))
        }

        val rules = dictionary.corrections()
        assertEquals(cap, rules.size)
        assertEquals(
            CorrectionPair(ruleName(20), "certa" + ruleName(20).removePrefix("erro")),
            rules.first()
        )
        assertEquals(
            CorrectionPair(ruleName(cap + 19), "certa" + ruleName(cap + 19).removePrefix("erro")),
            rules.last()
        )
    }

    // A revisão conta mutação: sobe em approve, learn, reject e forget — é o sinal que a prévia
    // incremental usa para descartar o memo.
    @Test
    fun revisionCountsMutations() {
        val dictionary = InMemoryPersonalDictionary()
        assertEquals(0L, dictionary.revision())

        dictionary.approve("casa")
        val afterApprove = dictionary.revision()
        assertTrue(afterApprove > 0L)

        dictionary.learnCorrection("caza", "casal")
        val afterLearn = dictionary.revision()
        assertTrue(afterLearn > afterApprove)

        dictionary.reject("casa")
        val afterReject = dictionary.revision()
        assertTrue(afterReject > afterLearn)

        dictionary.forgetCorrection("caza")
        assertTrue(dictionary.revision() > afterReject)
    }

    // O ditado repetido não é mutação: só move a revisão quando a lista de pendentes muda.
    @Test
    fun revisionOnlyMovesWhenPendingActuallyChanges() {
        val dictionary = InMemoryPersonalDictionary()
        val before = dictionary.revision()

        dictionary.suggestFrom("paciente caminha")
        assertTrue(dictionary.revision() > before)

        val stable = dictionary.revision()
        dictionary.suggestFrom("paciente caminha")
        assertEquals(stable, dictionary.revision())
    }

    // Revisão do motor (Item 3): teto de novas sugestões por chamada — um ditado longo não inunda a
    // lista de pendentes; o resto do texto espera o próximo ditado.
    @Test
    fun suggestFromReturnsAtMostTwelveNewSuggestionsPerCall() {
        val dictionary = InMemoryPersonalDictionary()
        val text = (0 until 30).joinToString(" ") { "termo$it" }

        val suggestions = dictionary.suggestFrom(text)

        assertEquals(InMemoryPersonalDictionary.MAX_NEW_SUGGESTIONS, suggestions.size)
        assertEquals("termo0", suggestions.first())
        assertEquals("termo11", suggestions.last())
        assertEquals(InMemoryPersonalDictionary.MAX_NEW_SUGGESTIONS, dictionary.pending().size)

        val secondBatch = dictionary.suggestFrom(text)
        assertEquals(InMemoryPersonalDictionary.MAX_NEW_SUGGESTIONS, secondBatch.size)
        assertEquals("termo12", secondBatch.first())
    }

    // Revisão do motor (Item 3): a fala clínica está cheia de palavras funcionais e de tempo que
    // nunca são termo do vocabulário. O candidato chega cru do texto e é comparado em minúsculas,
    // então "Ontem" com maiúscula casa com a entrada "ontem" da lista.
    @Test
    fun stopListFiltersSpokenFunctionalWords() {
        val dictionary = InMemoryPersonalDictionary()
        val suggestions = dictionary
            .suggestFrom("A paciente disse que está muito melhor e que Ontem também dormiu cedo")
            .map { it.lowercase() }

        assertTrue("paciente" in suggestions)
        assertTrue("disse" in suggestions)
        assertTrue("melhor" in suggestions)

        listOf("está", "muito", "ontem", "também").forEach { word ->
            assertTrue(word !in suggestions, "esperava '$word' fora das sugestões")
        }
        // "não" está na stop-list como proteção, mas nem chega a ser consultado: token de 3 letras
        // já cai no filtro de comprimento antes da lista.
        assertTrue("não" !in suggestions)
    }

    // Nomes só de letras, distintos: sufixo em base 26 (erroa…erroz, erroaa…) respeita a validação
    // da regra, que exige uma palavra sem dígito.
    private fun ruleName(index: Int): String {
        var n = index + 1
        var suffix = ""
        while (n > 0) {
            val letter = 'a' + (n - 1) % 26
            suffix = "$letter$suffix"
            n /= 26
        }
        return "erro$suffix"
    }
}