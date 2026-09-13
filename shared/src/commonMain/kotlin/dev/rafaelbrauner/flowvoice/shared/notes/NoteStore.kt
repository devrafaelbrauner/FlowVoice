package dev.rafaelbrauner.flowvoice.shared.notes

interface NoteStore {
    fun list(): List<Note>
    fun get(id: String): Note?
    fun upsert(note: Note): Note
    fun create(body: String, title: String? = null): Note
    fun delete(id: String): Boolean
}

interface NotePersist {
    fun load(): List<Note>
    fun save(notes: List<Note>)

    companion object {
        val NoOp: NotePersist = object : NotePersist {
            override fun load(): List<Note> = emptyList()
            override fun save(notes: List<Note>) = Unit
        }
    }
}
