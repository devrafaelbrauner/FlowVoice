package dev.rafaelbrauner.flowvoice.shared.notes

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NoteDictationMessage(val text: String, val isError: Boolean)

data class NoteDictationState(
    val noteId: String? = null,
    val message: NoteDictationMessage? = null
)

class NoteDictationCoordinator(private val notes: NoteStore) {
    private val stateFlow = MutableStateFlow(NoteDictationState())
    private var baselineSession = 0

    val state: StateFlow<NoteDictationState> = stateFlow.asStateFlow()

    val activeNoteId: String?
        get() = stateFlow.value.noteId

    fun attach(sessions: Flow<DictationPipelineSession>, scope: CoroutineScope): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessions.collect { onSession(it) }
        }

    fun begin(noteId: String, currentSession: DictationPipelineSession) {
        baselineSession = currentSession.id
        stateFlow.value = NoteDictationState(noteId = noteId)
    }

    fun onSession(session: DictationPipelineSession) {
        val target = activeNoteId ?: return
        if (session.id <= baselineSession) return
        if (session.target != DictationTarget.Note) {
            finish(null)
            return
        }
        when (val status = session.status) {
            is DictationPipelineStatus.Completed -> {
                append(target, status.text)
                finish(status.warning?.let { NoteDictationMessage(it, isError = false) })
            }
            is DictationPipelineStatus.Failed -> finish(NoteDictationMessage(status.message, isError = true))
            DictationPipelineStatus.Cancelled -> finish(null)
            else -> Unit
        }
    }

    fun forget(noteId: String): Boolean {
        if (activeNoteId != noteId) return false
        finish(null)
        return true
    }

    fun dismissMessage() {
        stateFlow.value = stateFlow.value.copy(message = null)
    }

    fun append(noteId: String, dictated: String) {
        val note = notes.get(noteId) ?: return
        if (dictated.isBlank()) return
        val title = if (note.title == UNTITLED && note.body.isBlank()) NoteText.titleFrom(dictated) else note.title
        notes.upsert(note.copy(title = title, body = NoteText.append(note.body, dictated)))
    }

    private fun finish(message: NoteDictationMessage?) {
        stateFlow.value = NoteDictationState(noteId = null, message = message)
    }

    companion object {
        const val UNTITLED = "Sem título"
    }
}

object NoteText {
    private const val TITLE_MAX = 48

    fun append(body: String, dictated: String): String {
        val text = dictated.trim()
        if (text.isEmpty()) return body
        if (body.isBlank()) return text
        return if (body.last().isWhitespace()) body + text else "$body $text"
    }

    fun titleFrom(text: String): String =
        text.trim().lineSequence().firstOrNull().orEmpty().trim().take(TITLE_MAX)
            .ifBlank { NoteDictationCoordinator.UNTITLED }
}
