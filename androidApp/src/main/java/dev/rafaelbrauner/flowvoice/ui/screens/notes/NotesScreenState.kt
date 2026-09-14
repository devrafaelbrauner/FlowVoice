package dev.rafaelbrauner.flowvoice.ui.screens.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.notes.NoteStore
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus

sealed interface NotesMessage {
    val text: String

    data class Warning(override val text: String) : NotesMessage

    data class Error(override val text: String) : NotesMessage
}

class NotesScreenState(
    private val store: NoteStore,
    initialNoteId: String? = null
) {
    var notes by mutableStateOf(store.list())
        private set
    var selectedId by mutableStateOf(NoteSelection.resolve(notes, initialNoteId))
        private set
    var panelOpen by mutableStateOf(true)
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set
    var dictationNoteId by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<NotesMessage?>(null)
        private set

    private var dictationBaseline: DictationPipelineStatus? = null
    private var dictationStarted = false

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
        if (dictationNoteId == id) dictationNoteId = null
        refresh()
        selectedId = NoteSelection.resolve(notes, next)
        return true
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }

    fun beginDictation(noteId: String, currentStatus: DictationPipelineStatus) {
        dictationNoteId = noteId
        dictationBaseline = currentStatus
        dictationStarted = false
        message = null
    }

    fun onPipelineStatus(status: DictationPipelineStatus) {
        val target = dictationNoteId ?: return
        if (!dictationStarted) {
            if (status == dictationBaseline && !status.isBusy) return
            dictationStarted = true
        }
        when (status) {
            is DictationPipelineStatus.Completed -> {
                appendDictation(target, status.text)
                message = status.warning?.let { NotesMessage.Warning(it) }
                finishDictation()
            }
            is DictationPipelineStatus.Failed -> {
                message = NotesMessage.Error(status.message)
                finishDictation()
            }
            DictationPipelineStatus.Cancelled -> finishDictation()
            else -> Unit
        }
    }

    fun showError(text: String) {
        message = NotesMessage.Error(text)
    }

    fun dismissMessage() {
        message = null
    }

    fun appendDictation(noteId: String, dictated: String) {
        val note = store.get(noteId) ?: return
        if (dictated.isBlank()) return
        val title = if (note.title == UNTITLED && note.body.isBlank()) {
            NoteBodies.titleFrom(dictated)
        } else {
            note.title
        }
        store.upsert(note.copy(title = title, body = NoteBodies.append(note.body, dictated)))
        refresh()
    }

    private fun finishDictation() {
        dictationNoteId = null
        dictationBaseline = null
        dictationStarted = false
    }

    companion object {
        const val UNTITLED = "Sem título"
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
    private const val TITLE_MAX = 48

    fun append(body: String, dictated: String): String {
        val text = dictated.trim()
        if (text.isEmpty()) return body
        if (body.isBlank()) return text
        return if (body.last().isWhitespace()) body + text else "$body $text"
    }

    fun titleFrom(text: String): String =
        text.trim().lineSequence().firstOrNull().orEmpty().trim().take(TITLE_MAX)
            .ifBlank { NotesScreenState.UNTITLED }

    fun snippet(body: String): String =
        body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
}
