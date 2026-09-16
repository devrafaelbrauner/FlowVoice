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
}
