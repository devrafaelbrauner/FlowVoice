package dev.rafaelbrauner.flowvoice.ui.screens.notes

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.notes.InMemoryNoteStore
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesScreenStateTest {
    private var clock = 1_000L
    private var ids = 0
    private val store = InMemoryNoteStore(clock = { clock++ }, ids = { "n${ids++}" })

    private fun completed(text: String, warning: String? = null) = DictationPipelineStatus.Completed(
        text = text,
        insertion = TextInsertionResult(false, "acessibilidade", "campo do próprio FlowVoice"),
        warning = warning
    )

    @Test
    fun selectsRequestedNoteOrFallsBackToMostRecent() {
        val older = store.create("primeira")
        val newer = store.create("segunda")

        assertEquals(older.id, NotesScreenState(store, older.id).selectedId)
        assertEquals(newer.id, NotesScreenState(store, "inexistente").selectedId)
        assertNull(NotesScreenState(InMemoryNoteStore(), null).selectedId)
    }

    @Test
    fun panelTogglesBetweenOpenAndCollapsed() {
        val state = NotesScreenState(store)
        assertTrue(state.panelOpen)
        state.togglePanel()
        assertFalse(state.panelOpen)
        state.togglePanel()
        assertTrue(state.panelOpen)
    }

    @Test
    fun newNoteIsSelectedAndStartsUntitled() {
        store.create("existente")
        val state = NotesScreenState(store)

        val created = state.createNote()

        assertEquals(created.id, state.selectedId)
        assertEquals(NotesScreenState.UNTITLED, state.selected?.title)
        assertEquals(2, state.notes.size)
    }

    @Test
    fun editingTitleAndBodyPersistsInStore() {
        val note = store.create("rascunho")
        val state = NotesScreenState(store, note.id)

        state.updateTitle(note.id, "Reunião")
        state.updateBody(note.id, "Fechamos o escopo.")

        assertEquals("Reunião", store.get(note.id)?.title)
        assertEquals("Fechamos o escopo.", store.get(note.id)?.body)
        assertEquals("Reunião", state.selected?.title)
    }

    @Test
    fun deleteNeedsConfirmationAndSelectsNeighbour() {
        val a = store.create("a")
        val b = store.create("b")
        val c = store.create("c")
        val state = NotesScreenState(store, b.id)

        assertFalse(state.onDeleteTap(b.id))
        assertEquals(b.id, state.pendingDeleteId)
        assertTrue(store.get(b.id) != null)

        assertTrue(state.onDeleteTap(b.id))
        assertNull(store.get(b.id))
        assertEquals(a.id, state.selectedId)
        assertNull(state.pendingDeleteId)
        assertEquals(listOf(c.id, a.id), state.notes.map { it.id })
    }

    @Test
    fun selectingAnotherNoteCancelsPendingDelete() {
        val a = store.create("a")
        val b = store.create("b")
        val state = NotesScreenState(store, a.id)

        state.onDeleteTap(a.id)
        state.select(b.id)

        assertNull(state.pendingDeleteId)
    }

    @Test
    fun appendAddsSpaceOnlyWhenNeeded() {
        assertEquals("ditado", NoteBodies.append("", "  ditado "))
        assertEquals("Fechamos. confirmar sexta", NoteBodies.append("Fechamos.", "confirmar sexta"))
        assertEquals("Linha\nnova", NoteBodies.append("Linha\n", "nova"))
        assertEquals("Texto", NoteBodies.append("Texto", "   "))
    }

    @Test
    fun completedDictationIsAppendedToTargetNoteAndTitlesBlankNote() {
        val state = NotesScreenState(store)
        val note = state.createNote()

        state.beginDictation(note.id, DictationPipelineStatus.Idle)
        state.onPipelineStatus(DictationPipelineStatus.Starting)
        state.onPipelineStatus(DictationPipelineStatus.Recording)
        state.onPipelineStatus(DictationPipelineStatus.Transcribing)
        state.onPipelineStatus(completed("Bom dia, Marina.\nsegunda linha"))

        val saved = store.get(note.id)!!
        assertEquals("Bom dia, Marina.\nsegunda linha", saved.body)
        assertEquals("Bom dia, Marina.", saved.title)
        assertNull(state.dictationNoteId)
        assertNull(state.message)
    }

    @Test
    fun staleCompletedStatusFromEarlierSessionIsIgnored() {
        val note = store.create("corpo")
        val state = NotesScreenState(store, note.id)
        val stale = completed("texto antigo")

        state.beginDictation(note.id, stale)
        state.onPipelineStatus(stale)

        assertEquals("corpo", store.get(note.id)?.body)
        assertEquals(note.id, state.dictationNoteId)

        state.onPipelineStatus(DictationPipelineStatus.Recording)
        state.onPipelineStatus(completed("novo"))
        assertEquals("corpo novo", store.get(note.id)?.body)
    }

    @Test
    fun warningAndFailureAreSurfacedAsMessages() {
        val note = store.create("corpo")
        val state = NotesScreenState(store, note.id)

        state.beginDictation(note.id, DictationPipelineStatus.Idle)
        state.onPipelineStatus(DictationPipelineStatus.Transcribing)
        state.onPipelineStatus(completed("parcial", warning = "Trecho 2 de 3 falhou (timeout): texto incompleto"))
        assertIs<NotesMessage.Warning>(state.message)
        assertEquals("corpo parcial", store.get(note.id)?.body)

        state.beginDictation(note.id, completed("parcial"))
        state.onPipelineStatus(DictationPipelineStatus.Failed("Nenhum trecho transcrito (chave OpenRouter ausente ou inválida)"))
        val error = assertIs<NotesMessage.Error>(state.message)
        assertTrue(error.text.contains("chave OpenRouter"))
        assertNull(state.dictationNoteId)
    }

    @Test
    fun startFailureConflatedWithoutBusyStatusIsStillConsumed() {
        val note = store.create("corpo")
        val state = NotesScreenState(store, note.id)

        state.beginDictation(note.id, DictationPipelineStatus.Idle)
        state.onPipelineStatus(DictationPipelineStatus.Failed("microfone indisponível"))

        assertIs<NotesMessage.Error>(state.message)
        assertNull(state.dictationNoteId)
    }

    @Test
    fun cancelledDictationLeavesNoteUntouched() {
        val note = store.create("corpo")
        val state = NotesScreenState(store, note.id)

        state.beginDictation(note.id, DictationPipelineStatus.Idle)
        state.onPipelineStatus(DictationPipelineStatus.Recording)
        state.onPipelineStatus(DictationPipelineStatus.Cancelled)

        assertEquals("corpo", store.get(note.id)?.body)
        assertNull(state.dictationNoteId)
    }

    @Test
    fun statusesWithoutActiveDictationAreIgnored() {
        val note = store.create("corpo")
        val state = NotesScreenState(store, note.id)

        state.onPipelineStatus(completed("de outro lugar"))

        assertEquals("corpo", store.get(note.id)?.body)
    }

    @Test
    fun snippetUsesFirstNonBlankLine() {
        assertEquals("café, azeite", NoteBodies.snippet("\n  café, azeite\npilha"))
        assertEquals("", NoteBodies.snippet("   "))
    }
}
