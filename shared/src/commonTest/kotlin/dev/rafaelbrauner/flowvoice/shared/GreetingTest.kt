package dev.rafaelbrauner.flowvoice.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun greetingContainsNameAndPlatform() {
        val result = greeting("FlowVoice")
        assertTrue(result.contains("FlowVoice"))
        assertTrue(result.contains(platformName()))
    }
}