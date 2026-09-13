package dev.rafaelbrauner.flowvoice.shared.notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryNoteStoreTest {
    @Test
    fun createEditDeleteAndOrderByUpdatedAt() {
        var now = 10L
        var next = 1
        val store = InMemoryNoteStore(clock = { now }, ids = { "n${next++}" })

        val first = store.create("Primeiro corpo")
        now = 20L
        val second = store.create("Segunda nota", title = "Clínico")
        assertEquals("Primeiro corpo", first.title)
        assertEquals("Clínico", second.title)
        assertEquals(listOf("n2", "n1"), store.list().map { it.id })

        now = 30L
        store.upsert(first.copy(body = "Atualizado"))
        assertEquals("Atualizado", store.get("n1")?.body)
        assertEquals(listOf("n1", "n2"), store.list().map { it.id })

        assertTrue(store.delete("n2"))
        assertNull(store.get("n2"))
        assertEquals(listOf("n1"), store.list().map { it.id })
    }
}
