package dev.rafaelbrauner.flowvoice.service

import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService.InsertResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuardedFieldInserterTest {
    private val own = "dev.rafaelbrauner.flowvoice"
    private val notes = "com.samsung.android.app.notes"
    private val bank = "com.bank"

    private class FakeField(override val ownPackage: String) : FocusedFieldAccess {
        var focused: String? = null
        var input: Int? = 1
        var password = false
        var text = ""
        val reads = mutableListOf<String?>()
        val rewrites = mutableListOf<String>()
        var rewriteAllowed = true

        override fun focusedPackage(): String? = focused
        override fun currentInputGeneration(): Int? = input
        override fun inputIsPassword(): Boolean = password

        override fun textBeforeCursor(limit: Int): String? {
            reads += focused
            return text.takeLast(limit)
        }

        override fun insertDirect(text: String, deleteBefore: Int, excludedPackage: String?): InsertResult {
            this.text = this.text.dropLast(deleteBefore) + text
            return InsertResult(true, "commitText", "ok")
        }

        override fun insertFallback(text: String, deleteBefore: Int, excludedPackage: String?): InsertResult =
            InsertResult(false, "ACTION_SET_TEXT", "não usado")

        override fun rewriteTail(text: String, deleteBefore: Int, excludedPackage: String): InsertResult? {
            if (!rewriteAllowed) return null
            rewrites += text
            this.text = this.text.dropLast(deleteBefore) + text
            return InsertResult(true, "ACTION_SET_TEXT", "ok")
        }
    }

    private val field = FakeField(own)
    private val inserter = GuardedFieldInserter { field }

    private fun startIn(pkg: String) {
        field.focused = pkg
        inserter.captureTarget()
    }

    @Test
    fun fieldIsReadOnlyInTheDestinationWhereTheDictationStarted() {
        startIn(notes)
        field.text = "Paciente"
        assertEquals("Paciente", inserter.readBeforeCursor(32))

        field.focused = bank
        field.text = "minhaSenha123"

        assertNull(inserter.readBeforeCursor(32))
        assertEquals(listOf<String?>(notes), field.reads)
    }

    @Test
    fun flowVoiceOwnFieldIsNeverRead() {
        startIn(notes)
        field.focused = own

        assertNull(inserter.readBeforeCursor(32))
        assertTrue(field.reads.isEmpty())
    }

    @Test
    fun passwordFieldIsNeverReadEvenInTheDestinationApp() {
        startIn(bank)
        field.password = true

        assertNull(inserter.readBeforeCursor(32))
        assertTrue(field.reads.isEmpty())
    }

    @Test
    fun anotherFieldOfTheSameAppIsNotReadAfterTheFirstInsertion() {
        startIn(notes)
        assertTrue(inserter.insertWithoutTap(" febre.", 0).success)

        field.input = 2

        assertNull(inserter.readBeforeCursor(32))
        assertTrue(field.reads.isEmpty())
    }

    @Test
    fun unknownDestinationIsNotRead() {
        field.focused = notes

        assertNull(inserter.readBeforeCursor(32))
        assertTrue(field.reads.isEmpty())
    }

    @Test
    fun repairIsRefusedInAnotherAppWithoutTouchingIt() {
        startIn(notes)
        field.focused = bank

        val result = inserter.rewriteTail(1, " mostrou")

        assertFalse(result!!.success)
        assertTrue(field.rewrites.isEmpty())
    }

    @Test
    fun repairWithoutASafeRouteReportsNoRoute() {
        startIn(notes)
        field.rewriteAllowed = false

        assertNull(inserter.rewriteTail(1, " mostrou"))
    }
}
