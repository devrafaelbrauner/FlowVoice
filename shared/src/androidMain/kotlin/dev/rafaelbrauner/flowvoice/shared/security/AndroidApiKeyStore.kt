package dev.rafaelbrauner.flowvoice.shared.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidApiKeyStore(
    context: Context,
    fileName: String = "flowvoice_secrets"
) : ApiKeyStore {

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        fileName,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun saveApiKey(key: String): Unit = withContext(Dispatchers.IO) {
        prefs.edit().putString(API_KEY, key.trim()).apply()
    }

    override suspend fun loadApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(API_KEY, null)?.trim()?.ifEmpty { null }
    }

    override suspend fun clearApiKey(): Unit = withContext(Dispatchers.IO) {
        prefs.edit().remove(API_KEY).apply()
    }

    companion object {
        private const val API_KEY = "openrouter_api_key"
    }
}
