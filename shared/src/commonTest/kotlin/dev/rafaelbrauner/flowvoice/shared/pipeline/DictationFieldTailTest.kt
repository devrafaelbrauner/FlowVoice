package dev.rafaelbrauner.flowvoice.shared.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DictationFieldTailTest {
    @Test
    fun theFieldEndingExactlyWithTheDictationErasesItsLength() {
        val typed = "Hoje o dia está muito bonito."

        assertEquals(typed.length, DictationFieldTail.eraseLength(typed, typed))
    }

    // O caso do S26 (2026-09-16 10:29): o campo ficou com um ponto a mais no meio do ditado, o app
    // apagou o que achava ter escrito e sobrou a primeira letra — "HHoje o dia...".
    @Test
    fun theFieldWithOneCharacterMoreThanTheAppCountedErasesThatCharacterToo() {
        val typed = "Hoje o dia está muito bonito por isso iremos para a praia."
        val field = "Hoje o dia está muito bonito. por isso iremos para a praia."

        assertEquals(field.length, DictationFieldTail.eraseLength(field, typed))
    }

    @Test
    fun theTextTheUserAlreadyHadInTheFieldIsNeverErased() {
        val typed = "vamos à praia pela manhã."
        val field = "Anotação minha: vamos à praia pela manhã."

        assertEquals(typed.length, DictationFieldTail.eraseLength(field, typed))
    }

    // Letra diferente no meio: alguém digitou junto, e apagar dali comeria texto do usuário.
    @Test
    fun aFieldWhoseLettersDoNotMatchTheDictationErasesNothing() {
        val typed = "vamos à praia pela manhã."
        val field = "vamos à praia amanhã pela manhã."

        assertNull(DictationFieldTail.eraseLength(field, typed))
    }

    @Test
    fun aFieldMuchLongerThanTheDictationErasesNothing() {
        val typed = "vamos à praia."
        val field = "v a m o s   à   p r a i a ." + " ".repeat(40)

        assertNull(DictationFieldTail.eraseLength(field, typed))
    }

    @Test
    fun aFieldShorterThanTheDictationErasesNothing() {
        assertNull(DictationFieldTail.eraseLength("praia.", "vamos à praia."))
    }

    @Test
    fun anEmptyDictationErasesNothing() {
        assertNull(DictationFieldTail.eraseLength("qualquer coisa", ""))
    }
}
