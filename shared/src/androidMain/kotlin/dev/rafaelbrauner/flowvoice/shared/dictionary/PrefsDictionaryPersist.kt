package dev.rafaelbrauner.flowvoice.shared.dictionary

import android.content.Context

class PrefsDictionaryPersist(context: Context) : DictionaryPersist {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun loadApproved(): List<String> =
        prefs.getStringSet(KEY, emptySet())?.toList().orEmpty()

    override fun saveApproved(terms: List<String>) {
        prefs.edit().putStringSet(KEY, terms.toSet()).apply()
    }

    companion object {
        private const val PREFS = "flowvoice_dictionary"
        private const val KEY = "approved_terms"
    }
}
