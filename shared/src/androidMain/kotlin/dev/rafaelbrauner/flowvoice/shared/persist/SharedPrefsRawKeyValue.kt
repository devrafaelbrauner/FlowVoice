package dev.rafaelbrauner.flowvoice.shared.persist

import android.content.SharedPreferences

class SharedPrefsRawKeyValue(private val prefs: SharedPreferences) : RawKeyValue {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
