package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.ui.navigation.FvDestination
import dev.rafaelbrauner.flowvoice.ui.shell.startDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OnboardingProgressTest {

    private val loggedIn = AppPreferences(loginCompleted = true)

    @Test
    fun nextPendingFollowsChecklistOrder() {
        assertEquals(
            OnboardingStep.Accessibility,
            OnboardingProgress(accessibility = false, microphone = false, transcription = false).nextPending
        )
        assertEquals(
            OnboardingStep.Microphone,
            OnboardingProgress(accessibility = true, microphone = false, transcription = false).nextPending
        )
        assertEquals(
            OnboardingStep.Transcription,
            OnboardingProgress(accessibility = true, microphone = true, transcription = false).nextPending
        )
    }

    @Test
    fun laterStepDoneDoesNotHideEarlierPendingStep() {
        val progress = OnboardingProgress(accessibility = false, microphone = true, transcription = true)

        assertEquals(OnboardingStep.Accessibility, progress.nextPending)
    }

    @Test
    fun allStepsDoneHasNoPendingStep() {
        val progress = OnboardingProgress(accessibility = true, microphone = true, transcription = true)

        assertTrue(progress.allDone)
        assertNull(progress.nextPending)
    }

    @Test
    fun finishingAllStepsCompletesOnboardingWithoutTheFinalTap() {
        val done = OnboardingProgress(accessibility = true, microphone = true, transcription = true)

        val after = loggedIn.afterOnboarding(done, skipped = false)

        assertEquals(FvDestination.Home, startDestination(after))
    }

    @Test
    fun pendingStepWithoutSkipKeepsOnboardingOnRelaunch() {
        val pending = OnboardingProgress(accessibility = true, microphone = false, transcription = true)

        val after = loggedIn.afterOnboarding(pending, skipped = false)

        assertEquals(FvDestination.Onboarding, startDestination(after))
    }

    @Test
    fun skippingIsRememberedSoOnboardingDoesNotReturnOnRelaunch() {
        val pending = OnboardingProgress(accessibility = false, microphone = false, transcription = false)

        val after = loggedIn.afterOnboarding(pending, skipped = true)

        assertEquals(FvDestination.Home, startDestination(after))
    }

    @Test
    fun completedOnboardingIsNeverReopenedByLosingAStep() {
        val completed = loggedIn.copy(onboardingCompleted = true)
        val lostAccessibility = OnboardingProgress(accessibility = false, microphone = true, transcription = true)

        assertSame(completed, completed.afterOnboarding(lostAccessibility, skipped = false))
    }

    @Test
    fun onDeviceModelInUseCompletesTranscriptionStepWithoutKey() {
        val progress = OnboardingProgress.of(
            accessibility = true,
            microphone = true,
            keyConfigured = false,
            effectiveEngine = TranscriptionEngine.Local
        )

        assertTrue(progress.isDone(OnboardingStep.Transcription))
        assertTrue(progress.allDone)
    }

    @Test
    fun neitherKeyNorOnDeviceModelLeavesTranscriptionStepPending() {
        val progress = OnboardingProgress.of(
            accessibility = true,
            microphone = true,
            keyConfigured = false,
            effectiveEngine = TranscriptionEngine.Cloud
        )

        assertFalse(progress.allDone)
        assertEquals(OnboardingStep.Transcription, progress.nextPending)
        assertEquals("Configurar a transcrição", progress.primaryLabel)
    }

    @Test
    fun keyWithCloudEngineCompletesTranscriptionStep() {
        val progress = OnboardingProgress.of(
            accessibility = true,
            microphone = true,
            keyConfigured = true,
            effectiveEngine = TranscriptionEngine.Cloud
        )

        assertTrue(progress.allDone)
    }
}
