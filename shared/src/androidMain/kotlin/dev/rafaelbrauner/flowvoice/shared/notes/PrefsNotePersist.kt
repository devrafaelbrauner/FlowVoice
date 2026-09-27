package dev.rafaelbrauner.flowvoice.shared.notes

import android.content.Context
import android.util.Log
import dev.rafaelbrauner.flowvoice.shared.persist.SharedPrefsRawKeyValue

class PrefsNotePersist(context: Context) : NotePersist by GuardedNotePersist(
    store = SharedPrefsRawKeyValue(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)),
    key = KEY,
    log = { Log.w(TAG, it) }
) {
    private companion object {
        const val PREFS = "flowvoice_notes"
        const val KEY = "notes_json"
        const val TAG = "FlowVoiceNotes"
    }
}
