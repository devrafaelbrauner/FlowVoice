package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BubbleGestureTest {
    private val gesture = BubbleGesture(touchSlopPx = 8f)

    @Test
    fun releaseWithoutMovingIsATap() {
        gesture.down(100f, 200f)

        assertEquals(BubbleGesture.End.Tap, gesture.up())
    }

    @Test
    fun jitterInsideTheSlopIsStillATapAndDoesNotMoveTheBubble() {
        gesture.down(100f, 200f)

        assertNull(gesture.move(104f, 203f))
        assertEquals(BubbleGesture.End.Tap, gesture.up())
    }

    @Test
    fun passingTheSlopStartsADragWithTheTotalDeltaFromTheFirstTouch() {
        gesture.down(100f, 200f)

        assertEquals(BubbleGesture.Drag(dx = 6f, dy = 6f), gesture.move(106f, 206f))
        assertEquals(BubbleGesture.Drag(dx = -40f, dy = 300f), gesture.move(60f, 500f))
    }

    @Test
    fun releasingADragNeverCountsAsATapEvenBackAtTheStartingPoint() {
        gesture.down(100f, 200f)
        gesture.move(160f, 200f)

        assertEquals(BubbleGesture.Drag(dx = 0f, dy = 0f), gesture.move(100f, 200f))
        assertEquals(BubbleGesture.End.DragEnd, gesture.up())
    }

    @Test
    fun cancelledGestureNeverTaps() {
        gesture.down(100f, 200f)
        assertEquals(BubbleGesture.End.None, gesture.cancel())
        assertEquals(BubbleGesture.End.None, gesture.up())

        gesture.down(100f, 200f)
        gesture.move(100f, 260f)
        assertEquals(BubbleGesture.End.DragEnd, gesture.cancel())
        assertEquals(BubbleGesture.End.None, gesture.up())
    }

    @Test
    fun eventsWithoutATouchDownAreIgnored() {
        assertNull(gesture.move(10f, 10f))
        assertEquals(BubbleGesture.End.None, gesture.up())
    }
}
