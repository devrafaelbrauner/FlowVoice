package dev.rafaelbrauner.flowvoice.shared.prefs

import android.content.Context
import android.util.Log
import dev.rafaelbrauner.flowvoice.shared.persist.SharedPrefsRawKeyValue

class PrefsPreferencesStore(context: Context) : PreferencesStore by GuardedPreferencesStore(
    store = SharedPrefsRawKeyValue(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)),
    key = KEY,
    log = { Log.w(TAG, it) }
) {
    private companion object {
        const val PREFS = "flowvoice_prefs"
        const val KEY = "prefs_json"
        const val TAG = "FlowVoicePrefs"
    }
}
