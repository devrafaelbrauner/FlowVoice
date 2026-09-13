package dev.rafaelbrauner.flowvoice.shared.sync

import dev.rafaelbrauner.flowvoice.shared.auth.AuthGateway
import dev.rafaelbrauner.flowvoice.shared.dictionary.PersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.notes.NoteStore
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore

class SyncEngine(
    private val auth: AuthGateway,
    private val notes: NoteStore,
    private val dictionary: PersonalDictionary,
    private val preferences: PreferencesStore,
    private val remote: RemoteSync,
    private val clock: () -> Long = { 0L }
) {
    suspend fun sync(): SyncResult {
        if (!auth.isSignedIn) {
            return SyncResult(pushed = false, pulled = false, mergedNotes = 0, mergedTerms = 0)
        }
        val remoteSnap = remote.pull()
        val localNotes = notes.list()
        val localTerms = dictionary.approved().map { it.surface }
        val localPrefs = preferences.read()
        val mergedNotes = mergeNotes(localNotes, remoteSnap?.notes.orEmpty())
        val mergedTerms = (localTerms + remoteSnap?.approvedTerms.orEmpty())
            .distinctBy { it.lowercase() }
        val mergedPrefs = localPrefs
        mergedNotes.forEach { notes.upsert(it) }
        mergedTerms.forEach { dictionary.approve(it) }
        preferences.write(mergedPrefs)
        val outgoing = SyncSnapshot(
            notes = notes.list(),
            approvedTerms = dictionary.approved().map { it.surface },
            preferences = preferences.read().copy(),
            updatedAtMs = clock()
        )
        remote.push(outgoing)
        return SyncResult(
            pushed = true,
            pulled = remoteSnap != null,
            mergedNotes = mergedNotes.size,
            mergedTerms = mergedTerms.size
        )
    }

    private fun mergeNotes(local: List<Note>, remote: List<Note>): List<Note> {
        val byId = linkedMapOf<String, Note>()
        (local + remote).forEach { note ->
            val current = byId[note.id]
            if (current == null || note.updatedAtMs >= current.updatedAtMs) {
                byId[note.id] = note
            }
        }
        return byId.values.toList()
    }
}
