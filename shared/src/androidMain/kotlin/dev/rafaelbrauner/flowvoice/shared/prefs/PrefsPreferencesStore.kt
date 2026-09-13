package dev.rafaelbrauner.flowvoice.shared.prefs

import android.content.Context
import kotlinx.serialization.json.Json

class PrefsPreferencesStore(context: Context) : PreferencesStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): AppPreferences {
        val raw = prefs.getString(KEY, null) ?: return AppPreferences()
        return runCatching { json.decodeFromString(AppPreferences.serializer(), raw) }
            .getOrDefault(AppPreferences())
    }

    override fun write(value: AppPreferences) {
        prefs.edit().putString(KEY, json.encodeToString(AppPreferences.serializer(), value)).apply()
    }

    companion object {
        private const val PREFS = "flowvoice_prefs"
        private const val KEY = "prefs_json"
    }
}
