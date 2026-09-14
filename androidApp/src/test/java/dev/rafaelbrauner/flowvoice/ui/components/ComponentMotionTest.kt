package dev.rafaelbrauner.flowvoice.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentMotionTest {

    @Test
    fun micPulseStartsVisibleAndFadesWhileExpanding() {
        val start = MicPulse.ring(0f)
        assertEquals(0f, start.spread)
        assertEquals(0.45f, start.alpha, 0.0001f)

        val peak = MicPulse.ring(0.7f)
        assertEquals(1f, peak.spread, 0.0001f)
        assertEquals(0f, peak.alpha, 0.0001f)

        val middle = MicPulse.ring(0.35f)
        assertTrue(middle.spread in 0f..1f)
        assertTrue(middle.alpha in 0f..0.45f)
    }

    @Test
    fun micPulseIsInvisibleAfterExpansion() {
        assertEquals(0f, MicPulse.ring(0.85f).alpha)
        assertEquals(0f, MicPulse.ring(1f).alpha)
        assertEquals(0f, MicPulse.ring(1f).spread, 0.0001f)
    }

    @Test
    fun joinAddsSeparatorOnlyWhenNeeded() {
        assertEquals(
            ProvisionalUnderline.Joined("Fechamos o escopo. precisa confirmar", 19),
            ProvisionalUnderline.join("Fechamos o escopo.", "precisa confirmar")
        )
        assertEquals(ProvisionalUnderline.Joined("texto ", 6), ProvisionalUnderline.join("texto ", ""))
        assertEquals(ProvisionalUnderline.Joined("prévia", 0), ProvisionalUnderline.join("", "prévia"))
        assertEquals(ProvisionalUnderline.Joined("a b", 1), ProvisionalUnderline.join("a", " b"))
    }

    @Test
    fun underlineSegmentsFollowLinesAndSkipEdgeWhitespace() {
        val text = "Fechamos o escopo. precisa confirmar com o financeiro"
        val provisionalStart = 19
        val lines = listOf(0 to 27, 27 to text.length)

        val segments = ProvisionalUnderline.segments(text, lines, provisionalStart, text.length)

        assertEquals(listOf(19 to 26, 27 to text.length), segments)
    }

    @Test
    fun underlineSegmentsAreEmptyWithoutProvisionalText() {
        val text = "tudo finalizado"
        assertTrue(ProvisionalUnderline.segments(text, listOf(0 to text.length), text.length, text.length).isEmpty())
    }
}
