package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertEquals

class CursorInsertionTest {

    @Test
    fun insertsAtCursorWithoutErasingExistingText() {
        val plan = CursorInsertion.plan(
            current = "Olá mundo",
            showingHint = false,
            selectionStart = 4,
            selectionEnd = 4,
            insert = "grande "
        )
        assertEquals("Olá grande mundo", plan.text)
        assertEquals(11, plan.cursor)
    }

    @Test
    fun replacesOnlyTheSelection() {
        val plan = CursorInsertion.plan(
            current = "exame de urina amanhã",
            showingHint = false,
            selectionStart = 14,
            selectionEnd = 9,
            insert = "sangue"
        )
        assertEquals("exame de sangue amanhã", plan.text)
        assertEquals(15, plan.cursor)
    }

    @Test
    fun invalidCursorAppendsAtTheEnd() {
        val plan = CursorInsertion.plan(
            current = "Paciente estável",
            showingHint = false,
            selectionStart = -1,
            selectionEnd = -1,
            insert = ", sem febre"
        )
        assertEquals("Paciente estável, sem febre", plan.text)
        assertEquals(plan.text.length, plan.cursor)
    }

    @Test
    fun cursorBeyondTextAppendsAtTheEnd() {
        val plan = CursorInsertion.plan(
            current = "abc",
            showingHint = false,
            selectionStart = 10,
            selectionEnd = 12,
            insert = "d"
        )
        assertEquals("abcd", plan.text)
        assertEquals(4, plan.cursor)
    }

    @Test
    fun hintTextIsTreatedAsEmpty() {
        val plan = CursorInsertion.plan(
            current = "Mensagem de texto",
            showingHint = true,
            selectionStart = 0,
            selectionEnd = 0,
            insert = "Oi"
        )
        assertEquals("Oi", plan.text)
        assertEquals(2, plan.cursor)
    }

    @Test
    fun emptyOrNullTextReceivesTheDictation() {
        assertEquals("Oi", CursorInsertion.plan(null, false, -1, -1, "Oi").text)
        assertEquals("Oi", CursorInsertion.plan("", false, 0, 0, "Oi").text)
    }

    // P144: a rota de fallback também precisa apagar o ponto que fechava a frase anterior.
    @Test
    fun deleteBeforeRemovesTheCharactersJustBeforeTheCursor() {
        val plan = CursorInsertion.plan(
            current = "O exame de sangue.",
            showingHint = false,
            selectionStart = 18,
            selectionEnd = 18,
            insert = " mostrou leucocitose.",
            deleteBefore = 1
        )

        assertEquals("O exame de sangue mostrou leucocitose.", plan.text)
        assertEquals(plan.text.length, plan.cursor)
    }

    @Test
    fun deleteBeforeNeverEatsTextBeforeTheStartOfTheField() {
        val plan = CursorInsertion.plan(
            current = "ab",
            showingHint = false,
            selectionStart = 1,
            selectionEnd = 1,
            insert = "X",
            deleteBefore = 5
        )

        assertEquals("Xb", plan.text)
        assertEquals(1, plan.cursor)
    }

    @Test
    fun usesUtf16IndicesAfterEmoji() {
        val current = "😀 fim"
        val plan = CursorInsertion.plan(
            current = current,
            showingHint = false,
            selectionStart = 2,
            selectionEnd = 2,
            insert = " meio"
        )
        assertEquals("😀 meio fim", plan.text)
        assertEquals(7, plan.cursor)
    }

    @Test
    fun cursorInsideSurrogatePairMovesAfterThePair() {
        val plan = CursorInsertion.plan(
            current = "a😀b",
            showingHint = false,
            selectionStart = 2,
            selectionEnd = 2,
            insert = "X"
        )
        assertEquals("a😀Xb", plan.text)
        assertEquals(4, plan.cursor)
    }
}
