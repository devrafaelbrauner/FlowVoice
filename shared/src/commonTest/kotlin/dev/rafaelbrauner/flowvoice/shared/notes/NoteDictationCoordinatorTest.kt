package dev.rafaelbrauner.flowvoice.shared.notes

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
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
    private val status = MutableStateFlow<DictationPipelineStatus>(DictationPipelineStatus.Idle)

    private fun completed(text: String, warning: String? = null) = DictationPipelineStatus.Completed(
        text = text,
        insertion = TextInsertionResult(false, "acessibilidade", "campo do próprio FlowVoice"),
        warning = warning
    )

    @Test
    fun completedTextIsAppendedToTargetNoteWithoutAnyScreenObserving() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(status, backgroundScope)
        val note = store.create(body = "")

        coordinator.begin(note.id, status.value)
        status.value = DictationPipelineStatus.Recording
        runCurrent()
        status.value = DictationPipelineStatus.Transcribing
        runCurrent()
        status.value = completed("Bom dia, Marina.\nsegunda linha")
        runCurrent()

        val saved = store.get(note.id)!!
        assertEquals("Bom dia, Marina.\nsegunda linha", saved.body)
        assertEquals("Bom dia, Marina.", saved.title)
        assertNull(coordinator.activeNoteId)
    }

    @Test
    fun activeDictationIsVisibleToANewObserverAfterTheScreenIsRecreated() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(status, backgroundScope)
        val note = store.create("corpo")

        coordinator.begin(note.id, status.value)
        status.value = DictationPipelineStatus.Recording
        runCurrent()

        assertEquals(note.id, coordinator.state.value.noteId)
        status.value = completed("novo")
        runCurrent()
        assertEquals("corpo novo", store.get(note.id)?.body)
    }

    @Test
    fun warningAndFailureStayAvailableForTheScreen() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(status, backgroundScope)
        val note = store.create("corpo")

        coordinator.begin(note.id, status.value)
        status.value = DictationPipelineStatus.Transcribing
        runCurrent()
        status.value = completed("parcial", warning = "Trecho 2 de 3 falhou (timeout): texto incompleto")
        runCurrent()
        val warning = coordinator.state.value.message!!
        assertFalse(warning.isError)
        assertEquals("corpo parcial", store.get(note.id)?.body)

        coordinator.begin(note.id, status.value)
        assertNull(coordinator.state.value.message)
        status.value = DictationPipelineStatus.Failed("Nenhum trecho transcrito (chave OpenRouter ausente ou inválida)")
        runCurrent()
        val failure = coordinator.state.value.message!!
        assertTrue(failure.isError)
        assertTrue(failure.text.contains("chave OpenRouter"))
        assertNull(coordinator.activeNoteId)
    }

    @Test
    fun sessionNotStartedForANoteIsIgnored() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(status, backgroundScope)
        val note = store.create("corpo")

        status.value = DictationPipelineStatus.Recording
        runCurrent()
        status.value = completed("de outro lugar")
        runCurrent()

        assertEquals("corpo", store.get(note.id)?.body)
        assertNull(coordinator.activeNoteId)
    }

    @Test
    fun forgettingTheActiveNoteDropsItsTarget() = runTest {
        val coordinator = NoteDictationCoordinator(store)
        coordinator.attach(status, backgroundScope)
        val note = store.create("corpo")

        coordinator.begin(note.id, status.value)
        assertFalse(coordinator.forget("outra"))
        assertTrue(coordinator.forget(note.id))
        status.value = completed("tarde demais")
        runCurrent()

        assertEquals("corpo", store.get(note.id)?.body)
        assertNull(coordinator.activeNoteId)
    }
}
