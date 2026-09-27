package dev.rafaelbrauner.flowvoice.ui.overlay

import android.view.WindowManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlayWindowFlagsTest {
    private fun notTouchable(flags: Int) = flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0

    private fun eachState(check: (hiddenByUser: Boolean, appInForeground: Boolean, mode: OverlayMode) -> Unit) {
        listOf(false, true).forEach { hidden ->
            listOf(false, true).forEach { foreground ->
                OverlayMode.entries.forEach { mode -> check(hidden, foreground, mode) }
            }
        }
    }

    @Test
    fun previewCardStaysTouchableWhenTheUserHidesTheBubbleDuringDirectDictation() {
        val bubbleWindowShown = BubbleVisibility.windowVisible(hiddenByUser = true, appInForeground = false, mode = OverlayMode.Preview)

        val card = OverlayWindowFlags.flags(OverlayWindow.Card, bubbleWindowShown)

        assertEquals(false, notTouchable(card))
        assertTrue(notTouchable(OverlayWindowFlags.flags(OverlayWindow.Bubble, bubbleWindowShown)))
    }

    @Test
    fun cardIsTouchableInEveryBubbleState() {
        eachState { hidden, foreground, mode ->
            val shown = BubbleVisibility.windowVisible(hidden, foreground, mode)
            assertEquals(false, notTouchable(OverlayWindowFlags.flags(OverlayWindow.Card, shown)), "$hidden $foreground $mode")
        }
    }

    @Test
    fun bubbleWindowIgnoresTouchesExactlyWhenItIsGone() {
        eachState { hidden, foreground, mode ->
            val shown = BubbleVisibility.windowVisible(hidden, foreground, mode)
            assertEquals(!shown, notTouchable(OverlayWindowFlags.flags(OverlayWindow.Bubble, shown)), "$hidden $foreground $mode")
        }
    }

    @Test
    fun bothWindowsNeverTakeFocusFromTheFieldBeingDictatedInto() {
        OverlayWindow.entries.forEach { window ->
            listOf(false, true).forEach { shown ->
                val flags = OverlayWindowFlags.flags(window, shown)
                assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            }
        }
    }
}
