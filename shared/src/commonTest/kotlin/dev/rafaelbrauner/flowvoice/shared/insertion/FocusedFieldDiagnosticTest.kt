package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusedFieldDiagnosticTest {
    @Test
    fun describesFieldWithoutExposingItsContent() {
        val message = FocusedFieldDiagnostic.describe(
            className = "android.widget.EditText",
            editable = true,
            focused = true,
            password = false,
            textLength = 16
        )

        assertEquals(
            "classe=android.widget.EditText, editável=true, focado=true, senha=false, tamanho=16",
            message
        )
    }

    @Test
    fun passwordFieldReportsOnlyItsLength() {
        val message = FocusedFieldDiagnostic.describe(
            className = "android.widget.EditText",
            editable = true,
            focused = true,
            password = true,
            textLength = 8
        )

        assertTrue(message.contains("senha=true"))
        assertTrue(message.contains("tamanho=8"))
        assertFalse(message.contains("texto="))
    }

    @Test
    fun unknownClassIsMarked() {
        val message = FocusedFieldDiagnostic.describe(
            className = null,
            editable = false,
            focused = false,
            password = false,
            textLength = 0
        )

        assertTrue(message.startsWith("classe=?"))
    }
}
