package dev.rafaelbrauner.flowvoice.shared.notes

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class PrefsNotePersist(context: Context) : NotePersist {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<Note> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Note.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override fun save(notes: List<Note>) {
        prefs.edit()
            .putString(KEY, json.encodeToString(ListSerializer(Note.serializer()), notes))
            .apply()
    }

    companion object {
        private const val PREFS = "flowvoice_notes"
        private const val KEY = "notes_json"
    }
}
