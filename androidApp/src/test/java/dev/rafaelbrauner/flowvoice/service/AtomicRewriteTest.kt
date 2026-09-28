package dev.rafaelbrauner.flowvoice.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AtomicRewriteTest {
    private val edit = AtomicRewrite.PLAIN_EDITOR_CLASS
    private val note = "Paciente com sanguemostrou"

    private fun skip(
        className: String? = edit,
        nodeText: String? = note,
        spans: Boolean = false,
        selection: Int = note.length,
        field: AtomicRewrite.Field? = AtomicRewrite.Field(note, 0, note.length, note.length)
    ) = AtomicRewrite.skip(className, nodeText, spans, selection, selection, field)

    @Test
    fun plainEditTextWhoseNodeShowsTheWholeFieldCanBeRewritten() {
        assertNull(skip())
    }

    @Test
    fun partiallyExposedFieldIsLeftAlone() {
        val longer = "Anamnese.\n$note"
        assertEquals(AtomicRewrite.Skip.Partial, skip(field = AtomicRewrite.Field(longer, 0, longer.length, longer.length)))
        assertEquals(AtomicRewrite.Skip.Partial, skip(field = AtomicRewrite.Field(note, 10, note.length, note.length)))
        assertEquals(AtomicRewrite.Skip.Partial, skip(field = AtomicRewrite.Field(note, 0, 3, 3)))
        assertEquals(AtomicRewrite.Skip.Partial, skip(field = null))
    }

    @Test
    fun customOrWebEditorsAreLeftAlone() {
        assertEquals(AtomicRewrite.Skip.NotPlainEditor, skip(className = "android.webkit.WebView"))
        assertEquals(AtomicRewrite.Skip.NotPlainEditor, skip(className = "com.samsung.android.spen.SpenComposerView"))
        assertEquals(AtomicRewrite.Skip.NotPlainEditor, skip(className = null))
    }

    @Test
    fun textCarryingSpansIsLeftAlone() {
        assertEquals(AtomicRewrite.Skip.Formatted, skip(spans = true))
        assertEquals(AtomicRewrite.Skip.Formatted, skip(nodeText = null))
    }
}
