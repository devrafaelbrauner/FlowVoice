package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsertionTargetTest {

    @Test
    fun fieldOfOwnAppIsBlocked() {
        assertTrue(InsertionTarget.isOwnApp("dev.rafaelbrauner.flowvoice", OWN))
    }

    @Test
    fun fieldOfAnotherAppIsAllowed() {
        assertFalse(InsertionTarget.isOwnApp("com.google.android.apps.messaging", OWN))
        assertFalse(InsertionTarget.isOwnApp("dev.rafaelbrauner.flowvoice.other", OWN))
    }

    @Test
    fun unknownTargetIsNotTreatedAsOwnApp() {
        assertFalse(InsertionTarget.isOwnApp(null, OWN))
        assertFalse(InsertionTarget.isOwnApp("", OWN))
    }

    private companion object {
        const val OWN = "dev.rafaelbrauner.flowvoice"
    }
}
