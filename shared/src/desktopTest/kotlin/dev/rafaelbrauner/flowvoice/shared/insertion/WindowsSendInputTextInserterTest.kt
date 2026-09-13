package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsSendInputTextInserterTest {
    private val secretText = "consulta sigilosa 😀"

    @Test
    fun sendsAllEventsInOneBatchAndReportsLengthOnly() {
        val batches = mutableListOf<List<KeyboardInputSpec>>()
        val inserter = WindowsSendInputTextInserter { inputs -> batches += inputs; inputs.size }

        val result = inserter.insert(secretText)

        assertTrue(result.success)
        assertEquals(1, batches.size)
        assertEquals(SendInputMapping.toKeyboardInputs(KeyStrokePlanner.plan(secretText)), batches.single())
        assertFalse(result.message.contains("sigilosa"))
    }

    @Test
    fun partialAcceptanceIsReportedAsFailure() {
        val inserter = WindowsSendInputTextInserter { 0 }

        val result = inserter.insert("abc")

        assertFalse(result.success)
        assertTrue(result.message.contains("UIPI"))
    }

    @Test
    fun missingNativeLibraryIsReportedAsFailure() {
        val inserter = WindowsSendInputTextInserter { throw UnsatisfiedLinkError("user32") }

        assertFalse(inserter.insert("abc").success)
    }

    @Test
    fun emptyTextIsNotSent() {
        var called = false
        val inserter = WindowsSendInputTextInserter { called = true; it.size }

        assertFalse(inserter.insert("").success)
        assertFalse(called)
    }

    @Test
    fun unsupportedInserterNeverInsertsAndHidesContent() {
        val inserter = UnsupportedTextInserter("MacOs")

        val result = inserter.insert(secretText)

        assertFalse(inserter.isAvailable)
        assertFalse(result.success)
        assertFalse(result.message.contains("sigilosa"))
    }
}
