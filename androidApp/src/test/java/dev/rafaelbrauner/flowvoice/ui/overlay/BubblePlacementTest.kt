package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BubblePlacementTest {
    // S26 Ultra em retrato (1440×3120) e em paisagem, já descontadas barras do sistema e margem.
    private val portrait = SafeArea(left = 42, top = 150, right = 1398, bottom = 2980)
    private val landscape = SafeArea(left = 170, top = 42, right = 2950, bottom = 1398)
    private val size = 266

    @Test
    fun defaultPositionIsOnTheRightEdgeAboveTheKeyboard() {
        val point = BubblePlacement.pointOf(BubblePosition.Default, portrait, size)

        assertEquals(BubbleSide.Right, BubblePosition.Default.side)
        assertEquals(1398 - 266, point.x)
        assertEquals(150 + Math.round(0.3f * (2980 - 266 - 150)), point.y)
        assertTrue(point.y + size < 1652, "a bolha padrão fica acima do topo do teclado medido no S26")
    }

    @Test
    fun edgesOfTheFractionMapToTheTopAndTheLowestPointWhereTheBubbleFits() {
        assertEquals(150, BubblePlacement.pointOf(BubblePosition(BubbleSide.Left, 0f), portrait, size).y)
        assertEquals(2980 - 266, BubblePlacement.pointOf(BubblePosition(BubbleSide.Left, 1f), portrait, size).y)
        assertEquals(42, BubblePlacement.pointOf(BubblePosition(BubbleSide.Left, 1f), portrait, size).x)
    }

    @Test
    fun draggingIsLimitedToTheSafeArea() {
        val start = BubblePoint(1132, 925)

        assertEquals(BubblePoint(1398 - 266, 150), BubblePlacement.dragged(start, dx = 900f, dy = -5_000f, portrait, size))
        assertEquals(BubblePoint(42, 2980 - 266), BubblePlacement.dragged(start, dx = -5_000f, dy = 9_000f, portrait, size))
        assertEquals(BubblePoint(632, 1225), BubblePlacement.dragged(start, dx = -500.4f, dy = 300.4f, portrait, size))
    }

    @Test
    fun releasingSnapsToTheNearestSideAndKeepsTheHeight() {
        val nearLeft = BubblePlacement.snap(BubblePoint(500, 150), portrait, size)
        val nearRight = BubblePlacement.snap(BubblePoint(700, 2980 - 266), portrait, size)

        assertEquals(BubblePosition(BubbleSide.Left, 0f), nearLeft)
        assertEquals(BubbleSide.Right, nearRight.side)
        assertEquals(1f, nearRight.fraction)
    }

    @Test
    fun savedPositionSurvivesRotationAndComesBackToTheSamePlace() {
        val saved = BubblePlacement.snap(BubblePoint(1132, 1400), portrait, size)

        val inLandscape = BubblePlacement.pointOf(saved, landscape, size)
        val backInPortrait = BubblePlacement.pointOf(BubblePlacement.snap(inLandscape, landscape, size), portrait, size)

        assertEquals(2950 - 266, inLandscape.x)
        assertTrue(inLandscape.y in 42..(1398 - 266))
        assertTrue(kotlin.math.abs(backInPortrait.y - 1400) <= 2, "voltou a ${backInPortrait.y}")
        assertEquals(1132, backInPortrait.x)
    }

    @Test
    fun areaSmallerThanTheBubbleKeepsItAtTheTop() {
        val tiny = SafeArea(left = 0, top = 100, right = 200, bottom = 300)

        assertEquals(BubblePoint(0, 100), BubblePlacement.pointOf(BubblePosition(BubbleSide.Right, 0.8f), tiny, size))
        assertEquals(0f, BubblePlacement.snap(BubblePoint(0, 100), tiny, size).fraction)
    }

    @Test
    fun accessibilityActionsMoveUpDownAndToTheOtherSideWithinBounds() {
        val position = BubblePosition(BubbleSide.Right, 0.05f)

        assertEquals(BubblePosition(BubbleSide.Right, 0f), BubblePlacement.moved(position, BubbleMove.Up))
        assertEquals(0.15f, BubblePlacement.moved(position, BubbleMove.Down).fraction, 0.0001f)
        assertEquals(BubblePosition(BubbleSide.Left, 0.05f), BubblePlacement.moved(position, BubbleMove.OtherSide))
        assertEquals(1f, BubblePlacement.moved(BubblePosition(BubbleSide.Left, 0.95f), BubbleMove.Down).fraction)
    }

    @Test
    fun previewOnTheRightUpperHalfGrowsDownFromTheBubbleTowardsTheCenter() {
        val position = BubblePosition(BubbleSide.Right, 0.3f)
        val bubble = BubblePlacement.pointOf(position, portrait, size)

        val window = BubblePlacement.previewWindow(position, portrait, size, maxWidthPx = 1_000, screenHeight = 3120)

        assertFalse(window.fromBottom)
        assertEquals(1_000, window.width)
        assertEquals(1398 - 1_000, window.x)
        assertEquals(bubble.y, window.y)
    }

    @Test
    fun previewOnTheLeftLowerHalfGrowsUpSoTheBubbleDoesNotJump() {
        val position = BubblePosition(BubbleSide.Left, 0.8f)
        val bubble = BubblePlacement.pointOf(position, portrait, size)

        val window = BubblePlacement.previewWindow(position, portrait, size, maxWidthPx = 5_000, screenHeight = 3120)

        assertTrue(window.fromBottom)
        assertEquals(1398 - 42, window.width)
        assertEquals(42, window.x)
        assertEquals(3120 - (bubble.y + size), window.y)
    }

    @Test
    fun storedValuesAreDecodedDefensively() {
        assertEquals(BubblePosition(BubbleSide.Left, 0.6f), BubblePosition.decode("left", 0.6f))
        assertEquals(BubblePosition.Default, BubblePosition.decode(null, 0.6f))
        assertEquals(BubblePosition.Default, BubblePosition.decode("meio", 0.6f))
        assertEquals(BubblePosition(BubbleSide.Right, 1f), BubblePosition.decode("right", 7f))
        assertEquals(BubblePosition.Default, BubblePosition.decode("right", Float.NaN))
        assertEquals("left", BubblePosition(BubbleSide.Left, 0.2f).encodedSide)
    }
}
