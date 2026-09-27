package dev.rafaelbrauner.flowvoice.notes

import dev.rafaelbrauner.flowvoice.shared.notes.InMemoryNoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.notes.NotePersist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedNotePersistTest {
    private class RecordingPersist : NotePersist {
        val saves = mutableListOf<List<Note>>()
        override fun load(): List<Note> = emptyList()
        override fun save(notes: List<Note>) {
            saves += notes
        }
    }

    private val disk = RecordingPersist()
    private val scope = TestScope()
    private val persist = DebouncedNotePersist(disk, scope, delayMs = 500)
    private val store = InMemoryNoteStore(persist)

    private fun typeBody(id: String, text: String) {
        text.indices.forEach { end ->
            store.upsert(store.get(id)!!.copy(body = text.substring(0, end + 1)))
        }
    }

    @Test
    fun typingANoteWritesOnceAfterThePauseWithTheLastText() {
        val note = store.create("")
        scope.advanceTimeBy(600)
        scope.runCurrent()
        disk.saves.clear()

        typeBody(note.id, "Paciente refere dispneia")
        scope.advanceTimeBy(499)
        scope.runCurrent()
        assertTrue(disk.saves.isEmpty())

        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertEquals(1, disk.saves.size)
        assertEquals("Paciente refere dispneia", disk.saves.single().single().body)
    }

    @Test
    fun flushWritesThePendingEditImmediatelyAndOnlyOnce() {
        val note = store.create("")
        typeBody(note.id, "febre")

        persist.flush()
        assertEquals("febre", disk.saves.single().single().body)

        scope.advanceTimeBy(1_000)
        scope.runCurrent()
        persist.flush()
        assertEquals(1, disk.saves.size)
    }

    @Test
    fun deletingANoteIsPersistedToo() {
        val note = store.create("rascunho")
        persist.flush()

        store.delete(note.id)
        scope.advanceTimeBy(600)
        scope.runCurrent()

        assertTrue(disk.saves.last().isEmpty())
    }
}
