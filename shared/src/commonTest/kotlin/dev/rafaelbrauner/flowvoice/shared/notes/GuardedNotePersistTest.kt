package dev.rafaelbrauner.flowvoice.shared.notes

import dev.rafaelbrauner.flowvoice.shared.persist.MapRawKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals

// Y8 (revisão de código): uma falha de decodificação virava lista vazia e o próximo `save`
// apagava todas as notas gravadas.
class GuardedNotePersistTest {
    private val good1 = """{"id":"1","title":"t1","body":"Dipirona 500 mg","updatedAtMs":1}"""
    private val good2 = """{"id":"2","title":"t2","body":"Nega alergias","updatedAtMs":2}"""
    private val bad = """{"id":"3","title":"t3"}"""

    @Test
    fun oneBadNoteDoesNotDropTheOthersAndTheRawPayloadIsBackedUp() {
        val raw = "[$good1,$bad,$good2]"
        val store = MapRawKeyValue(KEY to raw)
        val events = mutableListOf<String>()
        val notes = InMemoryNoteStore(GuardedNotePersist(store, KEY) { events += it }, clock = { 10L })

        assertEquals(setOf("1", "2"), notes.list().map { it.id }.toSet())
        notes.create("nova")

        assertEquals(raw, store.values["$KEY.bak"])
        assertEquals(3, notes.list().size)
        assertEquals(1, events.size)
    }

    @Test
    fun anUnreadablePayloadIsNeverOverwritten() {
        val raw = "[$good1,$good2"
        val store = MapRawKeyValue(KEY to raw)
        val notes = InMemoryNoteStore(GuardedNotePersist(store, KEY), clock = { 10L })

        notes.create("nota ditada depois da falha")

        assertEquals(raw, store.values[KEY])
        assertEquals(raw, store.values["$KEY.bak"])
        assertEquals(listOf("nota ditada depois da falha"), notes.list().map { it.body })
    }

    @Test
    fun anEarlierDifferentBackupIsKept() {
        val store = MapRawKeyValue(KEY to "lixo", "$KEY.bak" to "backup anterior")
        GuardedNotePersist(store, KEY).load()

        assertEquals("backup anterior", store.values["$KEY.bak"])
        assertEquals("lixo", store.values["$KEY.bak2"])
    }

    @Test
    fun readableNotesRoundTrip() {
        val store = MapRawKeyValue()
        val persist = GuardedNotePersist(store, KEY)
        val notes = InMemoryNoteStore(persist, clock = { 10L }, ids = { "a" })
        notes.create("corpo")

        assertEquals(listOf("corpo"), InMemoryNoteStore(GuardedNotePersist(store, KEY)).list().map { it.body })
        assertEquals(null, store.values["$KEY.bak"])
    }

    private companion object {
        const val KEY = "notes_json"
    }
}
