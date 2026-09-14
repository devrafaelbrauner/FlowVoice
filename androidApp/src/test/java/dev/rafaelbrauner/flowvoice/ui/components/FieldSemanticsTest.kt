package dev.rafaelbrauner.flowvoice.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FieldSemanticsTest {
    @Test
    fun emptyFieldIsNamedByItsLabel() {
        assertEquals("Corpo da nota", FieldSemantics.description(label = "Corpo da nota", value = ""))
    }

    @Test
    fun fieldWithTextExposesOnlyTheTyped() {
        assertNull(FieldSemantics.description(label = "Título da nota", value = "Sem título"))
        assertNull(FieldSemantics.description(label = "Novo termo", value = " "))
    }

    @Test
    fun fieldWithoutLabelHasNoDescription() {
        assertNull(FieldSemantics.description(label = null, value = ""))
    }
}
