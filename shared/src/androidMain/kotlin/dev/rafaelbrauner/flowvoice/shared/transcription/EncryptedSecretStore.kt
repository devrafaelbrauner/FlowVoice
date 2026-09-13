package dev.rafaelbrauner.flowvoice.shared.transcription

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.IOException
import java.security.GeneralSecurityException

class EncryptedSecretStore(private val context: Context) : SecretStore {
    private val prefs: SharedPreferences = openOrReset()

    override fun readOpenRouterKey(): String? =
        try {
            prefs.getString(KEY_OPENROUTER, null)?.takeIf { it.isNotBlank() }
        } catch (error: SecurityException) {
            Log.w(TAG, "Chave OpenRouter ilegível; removida do cofre (${error.javaClass.simpleName})")
            prefs.edit().remove(KEY_OPENROUTER).apply()
            null
        }

    override fun writeOpenRouterKey(value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "key must not be blank" }
        prefs.edit().putString(KEY_OPENROUTER, trimmed).apply()
    }

    override fun clearOpenRouterKey() {
        prefs.edit().remove(KEY_OPENROUTER).apply()
    }

    private fun openOrReset(): SharedPreferences =
        try {
            open()
        } catch (error: GeneralSecurityException) {
            reset(error)
        } catch (error: IOException) {
            reset(error)
        }

    private fun reset(error: Exception): SharedPreferences {
        Log.w(TAG, "Cofre da chave ilegível; recriando vazio (${error.javaClass.simpleName})")
        context.deleteSharedPreferences(PREFS_NAME)
        return open()
    }

    private fun open(): SharedPreferences =
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    companion object {
        private const val TAG = "FlowVoiceSecrets"
        private const val PREFS_NAME = "flowvoice_secrets"
        private const val KEY_OPENROUTER = "openrouter_api_key"
    }
}
