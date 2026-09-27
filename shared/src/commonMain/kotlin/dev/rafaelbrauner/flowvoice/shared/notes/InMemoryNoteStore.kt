package dev.rafaelbrauner.flowvoice.shared.notes

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Id novo é UUID (N4): relógio + contador do processo colidia com o relógio voltando ou entre
// aparelhos, e `upsert` sobrescrevia outra nota. Ids já gravados ficam como estão.
@OptIn(ExperimentalUuidApi::class)
class InMemoryNoteStore(
    private val persist: NotePersist = NotePersist.NoOp,
    private val clock: () -> Long = { 0L },
    private val ids: () -> String = { Uuid.random().toString() }
) : NoteStore {
    private val notes = linkedMapOf<String, Note>()

    init {
        persist.load().sortedBy { it.updatedAtMs }.forEach { notes[it.id] = it }
    }

    override fun list(): List<Note> =
        notes.values.filterNot { it.isDeleted }.sortedByDescending { it.updatedAtMs }

    override fun get(id: String): Note? = notes[id]?.takeUnless { it.isDeleted }

    override fun all(): List<Note> = notes.values.toList()

    override fun upsert(note: Note): Note {
        val stored = note.copy(updatedAtMs = clock())
        notes[stored.id] = stored
        persist.save(notes.values.toList())
        return stored
    }

    override fun putMerged(note: Note) {
        if (notes[note.id] == note) return
        notes[note.id] = note
        persist.save(notes.values.toList())
    }

    override fun create(body: String, title: String?): Note {
        val trimmed = body.trim()
        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: trimmed.lineSequence().firstOrNull().orEmpty().take(48).ifBlank { "Sem título" }
        return upsert(
            Note(
                id = ids(),
                title = resolvedTitle,
                body = trimmed,
                updatedAtMs = clock()
            )
        )
    }

    // Fica a lápide, sem título nem corpo: o texto clínico sai do aparelho, o id não volta pelo sync.
    override fun delete(id: String): Boolean {
        val current = notes[id]?.takeUnless { it.isDeleted } ?: return false
        val now = clock()
        notes[id] = current.copy(title = "", body = "", updatedAtMs = now, deletedAtMs = now)
        persist.save(notes.values.toList())
        return true
    }
}
