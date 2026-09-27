package dev.rafaelbrauner.flowvoice.shared.notes

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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

    // N4 (revisão de código): id = relógio + contador colidia com o relógio voltando 1 ms, e a nota
    // nova sobrescrevia a outra.
    @Test
    fun newNoteIdsDoNotCollideWhenTheClockGoesBackwards() {
        var now = 1_000L
        val store = InMemoryNoteStore(clock = { now })
        val first = store.create("Primeira nota")
        now = 999L
        val second = store.create("Segunda nota")

        assertTrue(first.id != second.id)
        assertEquals(setOf("Primeira nota", "Segunda nota"), store.list().map { it.body }.toSet())
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(first.id))
    }

    // Y7: nota gravada antes da lápide existir continua decodificando.
    @Test
    fun aNoteStoredWithoutTheDeletionFieldStillDecodes() {
        val stored = Json.decodeFromString(
            ListSerializer(Note.serializer()),
            """[{"id":"1726000000000","title":"t","body":"corpo","updatedAtMs":5}]"""
        )
        assertEquals(listOf(Note("1726000000000", "t", "corpo", 5L)), stored)
        assertEquals(listOf("1726000000000"), InMemoryNoteStore(persistOf(stored)).list().map { it.id })
    }

    private fun persistOf(notes: List<Note>) = object : NotePersist {
        override fun load(): List<Note> = notes
        override fun save(notes: List<Note>) = Unit
    }
}
