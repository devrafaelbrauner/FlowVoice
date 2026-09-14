package dev.rafaelbrauner.flowvoice.shared.notes

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NoteDictationCoordinatorTest {
    private var clock = 1_000L
    private var ids = 0
    private val store = InMemoryNoteStore(clock = { clock++ }, ids = { "n${ids++}" })
    private val sessions = MutableStateFlow(session(0, DictationPipelineStatus.Idle))

    private fun session(id: Int, status: DictationPipelineStatus, target: DictationTarget = DictationTarget.Note) =
        DictationPipelineSession(id, target, status)

    private fun completed(text: String, warning: String? = null) = DictationPipelineStatus.Completed(
        text = text,
        insertion = TextInsertionResult(false, "acessibilidade", "campo do próprio FlowVoice"),
        warning = warning
    )

    @Test
    fun completedTextIsAppendedToTargetNoteWithoutAnyScreenObserving() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(sessions, backgroundScope)
        val note = store.create(body = "")

        coordinator.begin(note.id, sessions.value)
        sessions.value = session(1, DictationPipelineStatus.Recording)
        runCurrent()
        sessions.value = session(1, DictationPipelineStatus.Transcribing)
        runCurrent()
        sessions.value = session(1, completed("Bom dia, Marina.\nsegunda linha"))
        runCurrent()

        val saved = store.get(note.id)!!
        assertEquals("Bom dia, Marina.\nsegunda linha", saved.body)
        assertEquals("Bom dia, Marina.", saved.title)
        assertNull(coordinator.activeNoteId)
    }

    @Test
    fun activeDictationIsVisibleToANewObserverAfterTheScreenIsRecreated() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(sessions, backgroundScope)
        val note = store.create("corpo")

        coordinator.begin(note.id, sessions.value)
        sessions.value = session(1, DictationPipelineStatus.Recording)
        runCurrent()

        assertEquals(note.id, coordinator.state.value.noteId)
        sessions.value = session(1, completed("novo"))
        runCurrent()
        assertEquals("corpo novo", store.get(note.id)?.body)
    }

    @Test
    fun warningAndFailureStayAvailableForTheScreen() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(sessions, backgroundScope)
        val note = store.create("corpo")

        coordinator.begin(note.id, sessions.value)
        sessions.value = session(1, DictationPipelineStatus.Transcribing)
        runCurrent()
        sessions.value = session(1, completed("parcial", warning = "Trecho 2 de 3 falhou (timeout): texto incompleto"))
        runCurrent()
        val warning = coordinator.state.value.message!!
        assertFalse(warning.isError)
        assertEquals("corpo parcial", store.get(note.id)?.body)

        coordinator.begin(note.id, sessions.value)
        assertNull(coordinator.state.value.message)
        sessions.value = session(2, DictationPipelineStatus.Failed("Nenhum trecho transcrito (chave OpenRouter ausente ou inválida)"))
        runCurrent()
        val failure = coordinator.state.value.message!!
        assertTrue(failure.isError)
        assertTrue(failure.text.contains("chave OpenRouter"))
        assertNull(coordinator.activeNoteId)
    }

    @Test
    fun sessionNotStartedForANoteIsIgnored() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(sessions, backgroundScope)
        val note = store.create("corpo")

        sessions.value = session(1, DictationPipelineStatus.Recording)
        runCurrent()
        sessions.value = session(1, completed("de outro lugar"))
        runCurrent()

        assertEquals("corpo", store.get(note.id)?.body)
        assertNull(coordinator.activeNoteId)
    }

    @Test
    fun activeFieldSessionStartedAfterBeginDisarmsTheNoteAndNeverWritesToIt() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(sessions, backgroundScope)
        val note = store.create("corpo")

        coordinator.begin(note.id, sessions.value)
        sessions.value = session(1, DictationPipelineStatus.Recording, DictationTarget.ActiveField)
        runCurrent()

        assertNull(coordinator.activeNoteId)
        sessions.value = session(1, completed("texto do whatsapp"), DictationTarget.ActiveField)
        runCurrent()
        assertEquals("corpo", store.get(note.id)?.body)
        assertNull(coordinator.state.value.message)
    }

    @Test
    fun forgettingTheActiveNoteDropsItsTarget() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(sessions, backgroundScope)
        val note = store.create("corpo")

        coordinator.begin(note.id, sessions.value)
        assertFalse(coordinator.forget("outra"))
        assertTrue(coordinator.forget(note.id))
        sessions.value = session(1, completed("tarde demais"))
        runCurrent()

        assertEquals("corpo", store.get(note.id)?.body)
        assertNull(coordinator.activeNoteId)
    }
}
