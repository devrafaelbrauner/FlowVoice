package dev.rafaelbrauner.flowvoice.shared.notes

interface NoteStore {
    fun list(): List<Note>
    fun get(id: String): Note?
    fun upsert(note: Note): Note
    fun create(body: String, title: String? = null): Note
    fun delete(id: String): Boolean

    // Para o sync (Y7): todas as notas, lápides incluídas, e a gravação de uma nota mesclada sem
    // carimbar a hora de agora, que apagaria a edição mais nova de outro aparelho.
    fun all(): List<Note>
    fun putMerged(note: Note)
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
