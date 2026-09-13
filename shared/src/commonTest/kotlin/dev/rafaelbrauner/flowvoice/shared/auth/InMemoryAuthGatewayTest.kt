package dev.rafaelbrauner.flowvoice.shared.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryAuthGatewayTest {
    @Test
    fun signInAndSignOut() {
        val auth = InMemoryAuthGateway()
        assertFalse(auth.isSignedIn)
        auth.signIn(AuthUser("1", "user@example.com"))
        assertTrue(auth.isSignedIn)
        assertEquals("user@example.com", auth.currentUser()?.email)
        auth.signOut()
        assertNull(auth.currentUser())
    }
}
