package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.ui.navigation.FvDestination
import dev.rafaelbrauner.flowvoice.ui.shell.startDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OnboardingProgressTest {

    private val loggedIn = AppPreferences(loginCompleted = true)

    @Test
    fun nextPendingFollowsChecklistOrder() {
        assertEquals(
            OnboardingStep.Accessibility,
            OnboardingProgress(accessibility = false, microphone = false, key = false).nextPending
        )
        assertEquals(
            OnboardingStep.Microphone,
            OnboardingProgress(accessibility = true, microphone = false, key = false).nextPending
        )
        assertEquals(
            OnboardingStep.Key,
            OnboardingProgress(accessibility = true, microphone = true, key = false).nextPending
        )
    }

    @Test
    fun laterStepDoneDoesNotHideEarlierPendingStep() {
        val progress = OnboardingProgress(accessibility = false, microphone = true, key = true)

        assertEquals(OnboardingStep.Accessibility, progress.nextPending)
    }

    @Test
    fun allStepsDoneHasNoPendingStep() {
        val progress = OnboardingProgress(accessibility = true, microphone = true, key = true)

        assertTrue(progress.allDone)
        assertNull(progress.nextPending)
    }

    @Test
    fun finishingAllStepsCompletesOnboardingWithoutTheFinalTap() {
        val done = OnboardingProgress(accessibility = true, microphone = true, key = true)

        val after = loggedIn.afterOnboarding(done, skipped = false)

        assertEquals(FvDestination.Home, startDestination(after))
    }

    @Test
    fun pendingStepWithoutSkipKeepsOnboardingOnRelaunch() {
        val pending = OnboardingProgress(accessibility = true, microphone = false, key = true)

        val after = loggedIn.afterOnboarding(pending, skipped = false)

        assertEquals(FvDestination.Onboarding, startDestination(after))
    }

    @Test
    fun skippingIsRememberedSoOnboardingDoesNotReturnOnRelaunch() {
        val pending = OnboardingProgress(accessibility = false, microphone = false, key = false)

        val after = loggedIn.afterOnboarding(pending, skipped = true)

        assertEquals(FvDestination.Home, startDestination(after))
    }

    @Test
    fun completedOnboardingIsNeverReopenedByLosingAStep() {
        val completed = loggedIn.copy(onboardingCompleted = true)
        val lostAccessibility = OnboardingProgress(accessibility = false, microphone = true, key = true)

        assertSame(completed, completed.afterOnboarding(lostAccessibility, skipped = false))
    }
}
