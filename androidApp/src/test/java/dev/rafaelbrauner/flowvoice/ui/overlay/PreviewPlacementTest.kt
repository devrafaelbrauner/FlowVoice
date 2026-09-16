package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewPlacementTest {
    // S26 em retrato: faixa útil do fim da barra de status (150) ao topo do teclado (1650).
    private val withKeyboard = PreviewSpace(top = 150, bottom = 1650)
    private val margin = 24

    @Test
    fun caretNearTheTopOpensTheCardBelowTheCaretLineAndTheLineAfterIt() {
        val placement = vertical(caret = 420..480, bubbleTop = 771)

        assertEquals(CardPlacement(edge = 771, fromBottom = false, maxHeight = 879, compact = false, avoiding = FocusSource.Caret), placement)
        assertClearOf(420..480, placement)
    }

    @Test
    fun caretNearTheKeyboardOpensTheCardAboveIt() {
        val placement = vertical(caret = 1500..1560, bubbleTop = 771)

        assertEquals(CardPlacement(edge = 1037, fromBottom = true, maxHeight = 887, compact = false, avoiding = FocusSource.Caret), placement)
        assertClearOf(1500..1560, placement)
    }

    @Test
    fun bubbleOverTheKeyboardStillKeepsTheCardInsideTheUsefulSpaceAboveTheCaret() {
        val placement = vertical(caret = 1500..1560, bubbleTop = 1700)

        assertEquals(CardPlacement(edge = 1476, fromBottom = true, maxHeight = 1326, compact = false, avoiding = FocusSource.Caret), placement)
    }

    @Test
    fun fieldCoveringTheWholeScreenUsesTheCaretLineAndPicksTheSideNearestTheBubble() {
        val placement = vertical(caret = 900..960, field = 300..1650, bubbleTop = 771)

        assertEquals(CardPlacement(edge = 876, fromBottom = true, maxHeight = 726, compact = false, avoiding = FocusSource.Caret), placement)
        assertClearOf(900..960, placement)
    }

    @Test
    fun whenNoSideFitsTheWholeCardTheLargerSideIsUsedWithAShorterCard() {
        val landscape = PreviewSpace(top = 42, bottom = 700)

        val placement = vertical(space = landscape, caret = 330..380, bubbleTop = 100, minHeight = 120)

        assertEquals(CardPlacement(edge = 306, fromBottom = true, maxHeight = 264, compact = true, avoiding = FocusSource.Caret), placement)
        assertClearOf(330..380, placement)
    }

    @Test
    fun withoutRoomForEvenACompactCardThePreviewIsNotShownRatherThanCoveringTheCaret() {
        val cramped = PreviewSpace(top = 42, bottom = 400)

        assertNull(vertical(space = cramped, caret = 150..250, bubbleTop = 100, minHeight = 120))
    }

    @Test
    fun unknownCaretFallsBackToTheBubbleHalf() {
        assertEquals(
            CardPlacement(edge = 771, fromBottom = false, maxHeight = 879, compact = false, avoiding = FocusSource.Unknown),
            vertical(caret = null, bubbleTop = 771, bubbleInLowerHalf = false)
        )
        assertEquals(
            CardPlacement(edge = 1566, fromBottom = true, maxHeight = 1416, compact = false, avoiding = FocusSource.Unknown),
            vertical(caret = null, bubbleTop = 1300, bubbleInLowerHalf = true)
        )
    }

    @Test
    fun smallFieldWithoutCaretIsAvoidedAsAWhole() {
        val placement = vertical(caret = null, field = 1520..1640, bubbleTop = 771)

        assertEquals(FocusSource.Field, placement?.avoiding)
        assertTrue(placement!!.fromBottom)
        assertClearOf(1520..1640, placement)
    }

    @Test
    fun bigFieldWithoutCaretCannotBeAvoidedAndFallsBackToTheBubbleHalf() {
        val placement = vertical(caret = null, field = 200..1650, bubbleTop = 771, bubbleInLowerHalf = false)

        assertEquals(FocusSource.Unknown, placement?.avoiding)
        assertEquals(771, placement?.edge)
    }

    @Test
    fun cardSpansFromTheBubbleTowardsTheCenterWithoutCoveringTheBubble() {
        assertEquals(
            CardSpan(x = 42, width = 1082),
            PreviewPlacement.horizontal(BubbleSide.Right, bubbleX = 1132, bubbleSize = 266, areaLeft = 42, areaRight = 1398, gap = 8, maxWidth = 1260)
        )
        assertEquals(
            CardSpan(x = 224, width = 900),
            PreviewPlacement.horizontal(BubbleSide.Right, bubbleX = 1132, bubbleSize = 266, areaLeft = 42, areaRight = 1398, gap = 8, maxWidth = 900)
        )
        assertEquals(
            CardSpan(x = 316, width = 1082),
            PreviewPlacement.horizontal(BubbleSide.Left, bubbleX = 42, bubbleSize = 266, areaLeft = 42, areaRight = 1398, gap = 8, maxWidth = 1260)
        )
    }

    private fun vertical(
        caret: IntRange?,
        bubbleTop: Int,
        field: IntRange? = null,
        space: PreviewSpace = withKeyboard,
        bubbleInLowerHalf: Boolean = false,
        minHeight: Int = 150
    ): CardPlacement? = PreviewPlacement.vertical(
        space = space,
        bubbleTop = bubbleTop,
        bubbleSize = 266,
        bubbleInLowerHalf = bubbleInLowerHalf,
        caret = caret,
        field = field,
        desiredHeight = 400,
        minHeight = minHeight,
        margin = margin
    )

    // O cartão pode ocupar de `edge` até `maxHeight` na direção dele; esse intervalo inteiro não pode tocar a linha.
    private fun assertClearOf(line: IntRange, placement: CardPlacement?) {
        val card = checkNotNull(placement)
        val span = if (card.fromBottom) (card.edge - card.maxHeight)..card.edge else card.edge..(card.edge + card.maxHeight)
        assertTrue(span.last <= line.first || span.first >= line.last, "cartão $span cobre a linha $line")
    }
}
