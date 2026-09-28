package dev.rafaelbrauner.flowvoice.ui.screens.notes

import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngineSelection
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteDictationGateTest {

    private fun decide(
        keyConfigured: Boolean,
        microphoneGranted: Boolean,
        engine: TranscriptionEngine = TranscriptionEngine.Cloud
    ) = NoteDictationGate.decide(TranscriptionEngineSelection.canDictate(engine, keyConfigured), microphoneGranted)

    @Test
    fun missingKeyWinsOverMissingMicrophoneSoNothingIsAskedOrCreated() {
        assertEquals(NoteStartAction.ConfigureKey, decide(keyConfigured = false, microphoneGranted = false))
        assertEquals(NoteStartAction.ConfigureKey, decide(keyConfigured = false, microphoneGranted = true))
    }

    @Test
    fun withKeyTheMicrophoneIsRequestedWhenMissing() {
        assertEquals(NoteStartAction.RequestMicrophone, decide(keyConfigured = true, microphoneGranted = false))
    }

    @Test
    fun withKeyAndMicrophoneDictationStarts() {
        assertEquals(NoteStartAction.Start, decide(keyConfigured = true, microphoneGranted = true))
    }

    @Test
    fun onDeviceModelInUseNeedsNoKey() {
        assertEquals(
            NoteStartAction.RequestMicrophone,
            decide(keyConfigured = false, microphoneGranted = false, engine = TranscriptionEngine.Local)
        )
        assertEquals(
            NoteStartAction.Start,
            decide(keyConfigured = false, microphoneGranted = true, engine = TranscriptionEngine.Local)
        )
    }
}
