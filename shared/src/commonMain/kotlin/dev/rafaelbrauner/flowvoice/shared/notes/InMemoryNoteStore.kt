package dev.rafaelbrauner.flowvoice.shared.notes

class InMemoryNoteStore(
    private val persist: NotePersist = NotePersist.NoOp,
    private val clock: () -> Long = { 0L },
    private val ids: () -> String = { (clock() + counter++).toString() }
) : NoteStore {
    private val notes = linkedMapOf<String, Note>()

    init {
        persist.load().sortedBy { it.updatedAtMs }.forEach { notes[it.id] = it }
    }

    override fun list(): List<Note> =
        notes.values.sortedByDescending { it.updatedAtMs }

    override fun get(id: String): Note? = notes[id]

    override fun upsert(note: Note): Note {
        val stored = note.copy(updatedAtMs = clock())
        notes[stored.id] = stored
        persist.save(notes.values.toList())
        return stored
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

    override fun delete(id: String): Boolean {
        val removed = notes.remove(id) != null
        if (removed) persist.save(notes.values.toList())
        return removed
    }

    companion object {
        private var counter = 0L
    }
}
