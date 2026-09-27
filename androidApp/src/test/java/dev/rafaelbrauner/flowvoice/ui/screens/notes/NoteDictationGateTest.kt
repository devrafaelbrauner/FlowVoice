package dev.rafaelbrauner.flowvoice.ui.screens.notes

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteDictationGateTest {

    @Test
    fun missingKeyWinsOverMissingMicrophoneSoNothingIsAskedOrCreated() {
        assertEquals(NoteStartAction.ConfigureKey, NoteDictationGate.decide(keyConfigured = false, microphoneGranted = false))
        assertEquals(NoteStartAction.ConfigureKey, NoteDictationGate.decide(keyConfigured = false, microphoneGranted = true))
    }

    @Test
    fun withKeyTheMicrophoneIsRequestedWhenMissing() {
        assertEquals(NoteStartAction.RequestMicrophone, NoteDictationGate.decide(keyConfigured = true, microphoneGranted = false))
    }

    @Test
    fun withKeyAndMicrophoneDictationStarts() {
        assertEquals(NoteStartAction.Start, NoteDictationGate.decide(keyConfigured = true, microphoneGranted = true))
    }
}
