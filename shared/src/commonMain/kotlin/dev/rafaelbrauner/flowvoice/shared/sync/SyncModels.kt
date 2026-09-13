package dev.rafaelbrauner.flowvoice.shared.sync

import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import kotlinx.serialization.Serializable

@Serializable
data class SyncSnapshot(
    val notes: List<Note> = emptyList(),
    val approvedTerms: List<String> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val updatedAtMs: Long = 0L
) {
    init {
        require(approvedTerms.none { it.contains("sk-", ignoreCase = true) })
    }
}

data class SyncResult(
    val pushed: Boolean,
    val pulled: Boolean,
    val mergedNotes: Int,
    val mergedTerms: Int
)
