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
