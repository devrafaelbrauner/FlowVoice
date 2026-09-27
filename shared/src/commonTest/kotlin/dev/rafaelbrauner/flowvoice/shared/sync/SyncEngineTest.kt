package dev.rafaelbrauner.flowvoice.shared.sync

import dev.rafaelbrauner.flowvoice.shared.auth.AuthUser
import dev.rafaelbrauner.flowvoice.shared.auth.InMemoryAuthGateway
import dev.rafaelbrauner.flowvoice.shared.dictionary.InMemoryPersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.notes.InMemoryNoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.notes.NotePersist
import dev.rafaelbrauner.flowvoice.shared.prefs.InMemoryPreferencesStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    // Y7 (revisão de código): B edita em t=200, A (cópia de t=100) sincroniza em t=300, B em t=400.
    // Antes a cópia velha de A virava N@300 e a edição de B se perdia.
    @Test
    fun aStaleCopyDoesNotOverwriteANewerEditFromAnotherDevice() = runTest {
        val remote = InMemoryRemoteSync()
        var nowA = 100L
        var nowB = 100L
        val deviceA = InMemoryNoteStore(clock = { nowA }, ids = { "n" })
        deviceA.create("original")
        signedInEngine(deviceA, remote).sync()
        val deviceB = InMemoryNoteStore(clock = { nowB })
        signedInEngine(deviceB, remote).sync()

        nowB = 200L
        deviceB.upsert(deviceB.get("n")!!.copy(body = "editado em B"))
        nowA = 300L
        signedInEngine(deviceA, remote).sync()
        nowB = 400L
        signedInEngine(deviceB, remote).sync()
        nowA = 500L
        signedInEngine(deviceA, remote).sync()

        assertEquals("editado em B", deviceB.get("n")?.body)
        assertEquals(200L, deviceB.get("n")?.updatedAtMs)
        assertEquals("editado em B", deviceA.get("n")?.body)
    }

    @Test
    fun syncKeepsTheMostRecentOrderAndDoesNotRewriteUnchangedNotes() = runTest {
        var now = 10L
        var saves = 0
        var next = 1
        val persist = object : NotePersist {
            override fun load(): List<Note> = emptyList()
            override fun save(notes: List<Note>) { saves++ }
        }
        val notes = InMemoryNoteStore(persist, clock = { now }, ids = { "n${next++}" })
        notes.create("antiga")
        now = 20L
        notes.create("nova")
        val remote = InMemoryRemoteSync()
        now = 90L
        signedInEngine(notes, remote).sync()
        signedInEngine(notes, remote).sync()

        assertEquals(listOf("n2" to 20L, "n1" to 10L), notes.list().map { it.id to it.updatedAtMs })
        assertEquals(2, saves)
    }

    @Test
    fun aNoteDeletedOnOneDeviceDoesNotComeBackFromTheRemote() = runTest {
        val remote = InMemoryRemoteSync()
        var now = 10L
        val deviceA = InMemoryNoteStore(clock = { now }, ids = { "n" })
        deviceA.create("para apagar")
        signedInEngine(deviceA, remote).sync()
        val deviceB = InMemoryNoteStore(clock = { now })
        signedInEngine(deviceB, remote).sync()

        now = 20L
        assertTrue(deviceA.delete("n"))
        signedInEngine(deviceA, remote).sync()
        signedInEngine(deviceB, remote).sync()

        assertNull(deviceA.get("n"))
        assertNull(deviceB.get("n"))
        assertTrue(remote.snapshot!!.notes.single().isDeleted)
        assertEquals("", remote.snapshot!!.notes.single().body)
    }

    private fun signedInEngine(notes: InMemoryNoteStore, remote: InMemoryRemoteSync): SyncEngine {
        val auth = InMemoryAuthGateway().apply { signIn(AuthUser("1", "a@b.c")) }
        return SyncEngine(auth, notes, InMemoryPersonalDictionary(), InMemoryPreferencesStore(), remote)
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
