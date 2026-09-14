package dev.rafaelbrauner.flowvoice.shared.sync

import dev.rafaelbrauner.flowvoice.shared.auth.AuthUser
import dev.rafaelbrauner.flowvoice.shared.auth.InMemoryAuthGateway
import dev.rafaelbrauner.flowvoice.shared.dictionary.InMemoryPersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.notes.InMemoryNoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.prefs.InMemoryPreferencesStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncEngineTest {
    @Test
    fun skipsWhenSignedOut() = runTest {
        val engine = engine(signedIn = false)
        val result = engine.sync()
        assertFalse(result.pushed)
        assertFalse(result.pulled)
    }

    @Test
    fun mergesRemoteWinsOnNewerNoteAndPushesWithoutSecrets() = runTest {
        val notes = InMemoryNoteStore(clock = { 50L }, ids = { "local" })
        notes.create("local")
        val remote = InMemoryRemoteSync()
        remote.push(
            SyncSnapshot(
                notes = listOf(Note("local", "remoto", "corpo remoto", 80L)),
                approvedTerms = listOf("Lactato"),
                updatedAtMs = 80L
            )
        )
        val dictionary = InMemoryPersonalDictionary()
        dictionary.approve("OpenRouter")
        val auth = InMemoryAuthGateway().apply { signIn(AuthUser("1", "a@b.c")) }
        val engine = SyncEngine(auth, notes, dictionary, InMemoryPreferencesStore(), remote, clock = { 90L })
        val result = engine.sync()
        assertTrue(result.pushed)
        assertEquals("corpo remoto", notes.get("local")?.body)
        assertTrue(dictionary.approved().any { it.surface == "Lactato" })
        assertTrue(dictionary.approved().any { it.surface == "OpenRouter" })
        assertTrue(remote.snapshot!!.approvedTerms.none { it.contains("sk-") })
    }

    private fun engine(signedIn: Boolean): SyncEngine {
        val auth = InMemoryAuthGateway()
        if (signedIn) auth.signIn(AuthUser("1", "a@b.c"))
        return SyncEngine(
            auth,
            InMemoryNoteStore(),
            InMemoryPersonalDictionary(),
            InMemoryPreferencesStore(),
            InMemoryRemoteSync()
        )
    }
}
