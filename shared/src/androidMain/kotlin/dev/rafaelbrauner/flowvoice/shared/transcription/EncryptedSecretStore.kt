package dev.rafaelbrauner.flowvoice.shared.transcription

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.SecretKey

class EncryptedSecretStore(private val context: Context) : SecretStore {
    private val prefs: SharedPreferences? = openVault()

    override fun readOpenRouterKey(): String? {
        val vault = prefs ?: return null
        return try {
            vault.getString(KEY_OPENROUTER, null)?.takeIf { it.isNotBlank() }
        } catch (error: SecurityException) {
            Log.w(TAG, "Chave OpenRouter ilegível; removida do cofre (${error.javaClass.simpleName})")
            vault.edit().remove(KEY_OPENROUTER).apply()
            null
        }
    }

    override fun writeOpenRouterKey(value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "key must not be blank" }
        val vault = prefs ?: throw SecretStoreUnavailableException("cofre da chave indisponível neste aparelho")
        vault.edit().putString(KEY_OPENROUTER, trimmed).apply()
    }

    override fun clearOpenRouterKey() {
        prefs?.edit()?.remove(KEY_OPENROUTER)?.apply()
    }

    private fun openVault(): SharedPreferences? {
        val opener = SecretVaultOpener(
            open = ::open,
            deleteVault = { context.deleteSharedPreferences(PREFS_NAME) },
            isCorruption = ::isKeysetCorruption,
            pause = { SystemClock.sleep(RETRY_DELAY_MS) }
        )
        return when (val outcome = opener.openVault()) {
            is SecretVaultOpener.Outcome.Opened -> {
                if (outcome.recreated) {
                    Log.w(TAG, "Cofre da chave corrompido; recriado vazio")
                }
                outcome.vault
            }
            is SecretVaultOpener.Outcome.Unavailable -> {
                Log.w(TAG, "Cofre da chave indisponível (${outcome.cause.javaClass.simpleName})")
                null
            }
        }
    }

    private fun open(): SharedPreferences =
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    // O Tink lança KeyStoreException quando a chave-mestra existe mas não funciona;
    // apagar o arquivo não resolve isso e perderia a chave. Keyset ilegível só conta
    // como corrupção se a chave-mestra ainda cifra.
    private fun isKeysetCorruption(error: Throwable): Boolean {
        val keysetError = error is IOException ||
            (error is GeneralSecurityException && error !is KeyStoreException)
        return keysetError && masterKeyUsable()
    }

    private fun masterKeyUsable(): Boolean = try {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (key == null) {
            false
        } else {
            Cipher.getInstance(MASTER_KEY_TRANSFORMATION).init(Cipher.ENCRYPT_MODE, key)
            true
        }
    } catch (error: Exception) {
        false
    }

    companion object {
        private const val TAG = "FlowVoiceSecrets"
        private const val PREFS_NAME = "flowvoice_secrets"
        private const val KEY_OPENROUTER = "openrouter_api_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
        private const val MASTER_KEY_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RETRY_DELAY_MS = 50L
    }
}
