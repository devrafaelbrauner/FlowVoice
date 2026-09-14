package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingProgressTest {

    @Test
    fun allStepsDoneOffersToStartDictating() {
        val progress = OnboardingProgress(accessibility = true, microphone = true, key = true)

        assertTrue(progress.allDone)
        assertEquals("Começar a ditar", progress.ctaLabel)
    }

    @Test
    fun anyMissingStepOffersToSkip() {
        listOf(
            OnboardingProgress(accessibility = false, microphone = true, key = true),
            OnboardingProgress(accessibility = true, microphone = false, key = true),
            OnboardingProgress(accessibility = true, microphone = true, key = false)
        ).forEach { progress ->
            assertFalse(progress.allDone)
            assertEquals("Pular por enquanto", progress.ctaLabel)
        }
    }
}
