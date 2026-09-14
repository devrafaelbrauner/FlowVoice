package dev.rafaelbrauner.flowvoice.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KeyMaskTest {
    @Test
    fun missingOrBlankKeyHasNoMask() {
        assertNull(KeyMask.mask(null))
        assertNull(KeyMask.mask("   "))
    }

    @Test
    fun openRouterKeyShowsOnlyPrefixDotsAndLastFour() {
        assertEquals("sk-or-v1-••••4f9c", KeyMask.mask("sk-or-v1-9b2a77c0d4e18f35ab6104f9c"))
    }

    @Test
    fun maskHidesTheMiddleAndTheKeyLength() {
        val shortKey = "sk-or-v1-a1b2c3d4e5f69f3e"
        val longKey = "sk-or-v1-" + "x".repeat(60) + "9f3e"

        val masked = KeyMask.mask(shortKey)

        assertEquals(masked, KeyMask.mask(longKey))
        assertFalse(masked!!.contains("a1b2"))
    }

    @Test
    fun unknownPrefixShowsOnlyLastFour() {
        assertEquals("••••wxyz", KeyMask.mask("pk-live-abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun shortKeyDoesNotRevealSuffix() {
        assertEquals("sk-••••", KeyMask.mask("sk-short"))
    }
}
