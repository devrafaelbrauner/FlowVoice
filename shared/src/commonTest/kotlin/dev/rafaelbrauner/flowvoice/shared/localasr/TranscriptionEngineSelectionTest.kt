package dev.rafaelbrauner.flowvoice.shared.localasr

import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine.Cloud
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine.Local
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptionEngineSelectionTest {
    @Test
    fun existingUsersStayOnTheCloudByDefault() {
        assertEquals(Cloud, AppPreferences().transcriptionEngine)
        assertEquals(Cloud, TranscriptionEngineSelection.effective(AppPreferences().transcriptionEngine, true, true))
    }

    @Test
    fun theLocalEngineNeedsChoiceModelAndPlatform() {
        assertEquals(Local, TranscriptionEngineSelection.effective(Local, modelInstalled = true, platformSupportsLocal = true))
        assertEquals(Cloud, TranscriptionEngineSelection.effective(Local, modelInstalled = false, platformSupportsLocal = true))
        assertEquals(Cloud, TranscriptionEngineSelection.effective(Local, modelInstalled = true, platformSupportsLocal = false))
        assertEquals(Cloud, TranscriptionEngineSelection.effective(Cloud, modelInstalled = true, platformSupportsLocal = true))
    }

    @Test
    fun onlyTheCloudNeedsTheKey() {
        assertTrue(TranscriptionEngineSelection.canDictate(Local, keyConfigured = false))
        assertTrue(TranscriptionEngineSelection.canDictate(Cloud, keyConfigured = true))
        assertFalse(TranscriptionEngineSelection.canDictate(Cloud, keyConfigured = false))
    }
}
