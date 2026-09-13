package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyStrokePlannerTest {
    @Test
    fun asciiProducesDownAndUpPerCharacterInOrder() {
        assertEquals(
            listOf(
                KeyStroke.Unicode('a', keyUp = false),
                KeyStroke.Unicode('a', keyUp = true),
                KeyStroke.Unicode('b', keyUp = false),
                KeyStroke.Unicode('b', keyUp = true)
            ),
            KeyStrokePlanner.plan("ab")
        )
    }

    @Test
    fun accentedCharacterIsSingleCodeUnit() {
        assertEquals(
            listOf(KeyStroke.Unicode('ç', keyUp = false), KeyStroke.Unicode('ç', keyUp = true)),
            KeyStrokePlanner.plan("ç")
        )
    }

    @Test
    fun emojiIsSentAsBothSurrogateCodeUnits() {
        val strokes = KeyStrokePlanner.plan("😀")

        assertEquals(
            listOf(
                KeyStroke.Unicode('\uD83D', keyUp = false),
                KeyStroke.Unicode('\uD83D', keyUp = true),
                KeyStroke.Unicode('\uDE00', keyUp = false),
                KeyStroke.Unicode('\uDE00', keyUp = true)
            ),
            strokes
        )
    }

    @Test
    fun allLineBreakStylesBecomeReturnKey() {
        val keys = KeyStrokePlanner.plan("a\r\nb\rc\n").filter { !it.keyUp }

        assertEquals(
            listOf(
                KeyStroke.Unicode('a', keyUp = false),
                KeyStroke.VirtualKey(KeyStrokePlanner.VK_RETURN, keyUp = false),
                KeyStroke.Unicode('b', keyUp = false),
                KeyStroke.VirtualKey(KeyStrokePlanner.VK_RETURN, keyUp = false),
                KeyStroke.Unicode('c', keyUp = false),
                KeyStroke.VirtualKey(KeyStrokePlanner.VK_RETURN, keyUp = false)
            ),
            keys
        )
    }

    @Test
    fun emptyTextHasNoStrokes() {
        assertTrue(KeyStrokePlanner.plan("").isEmpty())
    }
}
