package dev.rafaelbrauner.flowvoice.shared.dictionary

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dev.rafaelbrauner.flowvoice.shared.persist.SharedPrefsRawKeyValue

class PrefsDictionaryPersist(context: Context) : DictionaryPersist by persistFor(
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
) {
    private companion object {
        const val PREFS = "flowvoice_dictionary"
        const val LEGACY_KEY = "approved_terms"
        const val KEY = "approved_terms_json"
        const val TAG = "FlowVoiceDictionary"

        fun persistFor(prefs: SharedPreferences) = JsonDictionaryPersist(
            store = SharedPrefsRawKeyValue(prefs),
            key = KEY,
            legacy = object : JsonDictionaryPersist.LegacyTerms {
                override fun read(): Set<String>? = prefs.getStringSet(LEGACY_KEY, null)?.toSet()
                override fun clear() {
                    prefs.edit().remove(LEGACY_KEY).apply()
                }
            },
            log = { Log.w(TAG, it) }
        )
    }
}
