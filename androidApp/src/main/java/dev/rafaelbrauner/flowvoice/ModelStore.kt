package dev.rafaelbrauner.flowvoice

import android.content.Context
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionManager

interface ModelStore {
    fun saveModel(model: String)
    fun loadModel(): String?
}

class SharedPrefsModelStore(context: Context) : ModelStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun saveModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    override fun loadModel(): String? =
        prefs.getString(KEY_MODEL, null)?.trim()?.ifEmpty { null }
            ?: TranscriptionManager.DEFAULT_MODEL

    companion object {
        private const val PREFS = "flowvoice_settings"
        private const val KEY_MODEL = "transcription_model"
    }
}
