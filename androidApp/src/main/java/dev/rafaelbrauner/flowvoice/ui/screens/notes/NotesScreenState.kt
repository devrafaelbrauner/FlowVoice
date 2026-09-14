package dev.rafaelbrauner.flowvoice.ui.screens.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.notes.NoteDictationCoordinator
import dev.rafaelbrauner.flowvoice.shared.notes.NoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.NoteText
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus

sealed interface NotesMessage {
    val text: String

    data class Warning(override val text: String) : NotesMessage

    data class Error(override val text: String) : NotesMessage
}

class NotesScreenState(
    private val store: NoteStore,
    initialNoteId: String? = null,
    private val dictation: NoteDictationCoordinator = NoteDictationCoordinator(store)
) {
    var notes by mutableStateOf(store.list())
        private set
    var selectedId by mutableStateOf(NoteSelection.resolve(notes, initialNoteId))
        private set
    var panelOpen by mutableStateOf(true)
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set

    private var localMessage by mutableStateOf<NotesMessage?>(null)

    val dictationNoteId: String?
        get() = dictation.state.value.noteId

    val message: NotesMessage?
        get() = localMessage ?: dictation.state.value.message?.let {
            if (it.isError) NotesMessage.Error(it.text) else NotesMessage.Warning(it.text)
        }

    val selected: Note?
        get() = notes.firstOrNull { it.id == selectedId }

    fun refresh() {
        notes = store.list()
        selectedId = NoteSelection.resolve(notes, selectedId)
    }

    fun select(id: String) {
        if (notes.none { it.id == id }) return
        selectedId = id
        pendingDeleteId = null
    }

    fun togglePanel() {
        panelOpen = !panelOpen
    }

    fun createNote(): Note {
        val note = store.create(body = "")
        refresh()
        selectedId = note.id
        pendingDeleteId = null
        return note
    }

    fun updateTitle(id: String, title: String) {
        val note = store.get(id) ?: return
        if (note.title == title) return
        store.upsert(note.copy(title = title))
        refresh()
    }

    fun updateBody(id: String, body: String) {
        val note = store.get(id) ?: return
        if (note.body == body) return
        store.upsert(note.copy(body = body))
        refresh()
    }

    fun onDeleteTap(id: String): Boolean {
        if (pendingDeleteId != id) {
            pendingDeleteId = id
            return false
        }
        val next = NoteSelection.afterDelete(notes, id)
        store.delete(id)
        pendingDeleteId = null
        dictation.forget(id)
        refresh()
        selectedId = NoteSelection.resolve(notes, next)
        return true
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }

    fun beginDictation(noteId: String, currentStatus: DictationPipelineStatus) {
        localMessage = null
        dictation.begin(noteId, currentStatus)
    }

    fun onPipelineStatus(status: DictationPipelineStatus) {
        dictation.onStatus(status)
        refresh()
    }

    fun showError(text: String) {
        localMessage = NotesMessage.Error(text)
    }

    fun dismissMessage() {
        localMessage = null
        dictation.dismissMessage()
    }

    fun appendDictation(noteId: String, dictated: String) {
        dictation.append(noteId, dictated)
        refresh()
    }

    companion object {
        const val UNTITLED = NoteDictationCoordinator.UNTITLED
    }
}

object NoteSelection {
    fun resolve(notes: List<Note>, requestedId: String?): String? =
        notes.firstOrNull { it.id == requestedId }?.id ?: notes.firstOrNull()?.id

    fun afterDelete(notes: List<Note>, deletedId: String): String? {
        val index = notes.indexOfFirst { it.id == deletedId }
        if (index < 0) return notes.firstOrNull()?.id
        return notes.getOrNull(index + 1)?.id ?: notes.getOrNull(index - 1)?.id
    }
}

object NoteBodies {
    fun append(body: String, dictated: String): String = NoteText.append(body, dictated)

    fun titleFrom(text: String): String = NoteText.titleFrom(text)

    fun snippet(body: String): String =
        body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
}
