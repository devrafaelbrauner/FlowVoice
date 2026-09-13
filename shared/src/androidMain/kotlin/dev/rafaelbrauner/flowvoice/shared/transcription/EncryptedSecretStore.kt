package dev.rafaelbrauner.flowvoice.shared.transcription

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class EncryptedSecretStore(context: Context) : SecretStore {
    private val prefs = EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun readOpenRouterKey(): String? =
        prefs.getString(KEY_OPENROUTER, null)?.takeIf { it.isNotBlank() }

    override fun writeOpenRouterKey(value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "key must not be blank" }
        prefs.edit().putString(KEY_OPENROUTER, trimmed).apply()
    }

    override fun clearOpenRouterKey() {
        prefs.edit().remove(KEY_OPENROUTER).apply()
    }

    companion object {
        private const val PREFS_NAME = "flowvoice_secrets"
        private const val KEY_OPENROUTER = "openrouter_api_key"
    }
}
