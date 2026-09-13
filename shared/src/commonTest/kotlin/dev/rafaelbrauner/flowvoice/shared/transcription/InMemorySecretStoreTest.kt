package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InMemorySecretStoreTest {
    @Test
    fun persistsAndClearsKeyWithoutBlankValues() {
        val store = InMemorySecretStore()
        assertNull(store.readOpenRouterKey())
        store.writeOpenRouterKey("  sk-or-v1-testkey123456  ")
        assertEquals("sk-or-v1-testkey123456", store.readOpenRouterKey())
        assertFailsWith<IllegalArgumentException> { store.writeOpenRouterKey("   ") }
        store.clearOpenRouterKey()
        assertNull(store.readOpenRouterKey())
    }
}
