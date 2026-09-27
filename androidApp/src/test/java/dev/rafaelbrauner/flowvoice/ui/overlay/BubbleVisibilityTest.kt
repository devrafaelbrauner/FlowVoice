package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BubbleVisibilityTest {

    @Test
    fun idleBubbleHidesWhileFlowVoiceIsOnScreenAndReturnsInTheBackground() {
        assertFalse(BubbleVisibility.showBubble(hiddenByUser = false, appInForeground = true, mode = OverlayMode.Bubble))
        assertTrue(BubbleVisibility.showBubble(hiddenByUser = false, appInForeground = false, mode = OverlayMode.Bubble))
    }

    @Test
    fun liveDirectPreviewKeepsItsStopBubbleEvenInsideTheApp() {
        assertTrue(BubbleVisibility.showBubble(hiddenByUser = false, appInForeground = true, mode = OverlayMode.Preview))
        assertTrue(BubbleVisibility.showBubble(hiddenByUser = false, appInForeground = false, mode = OverlayMode.Preview))
    }

    @Test
    fun hideButtonWinsInEveryMode() {
        OverlayMode.entries.forEach { mode ->
            assertFalse(BubbleVisibility.showBubble(hiddenByUser = true, appInForeground = false, mode = mode))
            assertFalse(BubbleVisibility.showBubble(hiddenByUser = true, appInForeground = true, mode = mode))
        }
    }

    @Test
    fun barReplacesTheBubble() {
        assertFalse(BubbleVisibility.showBubble(hiddenByUser = false, appInForeground = false, mode = OverlayMode.Bar))
    }

    @Test
    fun overlayWindowIsGoneWheneverItWouldBeEmpty() {
        assertFalse(BubbleVisibility.windowVisible(hiddenByUser = false, appInForeground = true, mode = OverlayMode.Bubble))
        assertFalse(BubbleVisibility.windowVisible(hiddenByUser = true, appInForeground = false, mode = OverlayMode.Preview))
        assertTrue(BubbleVisibility.windowVisible(hiddenByUser = false, appInForeground = false, mode = OverlayMode.Bubble))
    }

    @Test
    fun barWindowStaysVisibleEvenWithTheBubbleHidden() {
        assertTrue(BubbleVisibility.windowVisible(hiddenByUser = true, appInForeground = true, mode = OverlayMode.Bar))
    }
}
