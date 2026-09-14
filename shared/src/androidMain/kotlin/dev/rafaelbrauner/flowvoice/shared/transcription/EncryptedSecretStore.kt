package dev.rafaelbrauner.flowvoice.shared.transcription

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException

class EncryptedSecretStore(private val context: Context) : SecretStore {
    private val vault = CipherSecretStore(
        cipher = KeystoreVaultCipher(),
        storage = PrefsVaultStorage(context),
        isCorruption = { it is AEADBadTagException || it is VaultKeyMissingException },
        onFailure = { event, error -> Log.w(TAG, "$event ${error.javaClass.simpleName}") }
    )

    init {
        migrateLegacyVault()
    }

    override fun readOpenRouterKey(): String? = vault.readOpenRouterKey()

    override fun writeOpenRouterKey(value: String) = vault.writeOpenRouterKey(value)

    override fun clearOpenRouterKey() = vault.clearOpenRouterKey()

    private fun migrateLegacyVault() {
        val outcome = try {
            LegacySecretMigration(
                legacyExists = ::legacyVaultExists,
                readLegacy = ::readLegacyKey,
                target = vault,
                deleteLegacy = ::deleteLegacyVault
            ).migrate()
        } catch (error: Exception) {
            LegacySecretMigration.Outcome.Kept(error)
        }
        when (outcome) {
            LegacySecretMigration.Outcome.NoLegacy -> Unit
            is LegacySecretMigration.Outcome.Kept ->
                Log.w(TAG, "legacy_vault_kept ${outcome.cause.javaClass.simpleName}")
            else -> Log.i(TAG, "legacy_vault_${outcome::class.simpleName}")
        }
    }

    private fun legacyVaultExists(): Boolean =
        File(File(context.applicationInfo.dataDir, "shared_prefs"), "$LEGACY_PREFS_NAME.xml").exists()

    // O cofre antigo só é aberto se o keyset e a chave-mestra já existirem: assim o
    // Tink nunca gera material novo, que é o caminho em que ele grava o keyset em claro.
    private fun readLegacyKey(): String? {
        val raw = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (raw.all.isEmpty()) return null
        if (!raw.contains(LEGACY_KEY_KEYSET) || !raw.contains(LEGACY_VALUE_KEYSET)) {
            throw GeneralSecurityException("keyset do cofre antigo ausente")
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(LEGACY_MASTER_KEY_ALIAS)) {
            throw KeyStoreException("chave-mestra do cofre antigo ausente")
        }
        val opened = SecretVaultOpener(
            open = ::openLegacyVault,
            deleteVault = {},
            isCorruption = { false },
            pause = { SystemClock.sleep(RETRY_DELAY_MS) }
        ).openVault()
        val legacy = when (opened) {
            is SecretVaultOpener.Outcome.Opened -> opened.vault
            is SecretVaultOpener.Outcome.Unavailable -> throw opened.cause
        }
        return legacy.getString(LEGACY_KEY_OPENROUTER, null)?.takeIf { it.isNotBlank() }
    }

    private fun openLegacyVault(): SharedPreferences =
        EncryptedSharedPreferences.create(
            LEGACY_PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun deleteLegacyVault() {
        if (!context.deleteSharedPreferences(LEGACY_PREFS_NAME)) {
            throw IOException("cofre antigo não removido")
        }
    }

    private companion object {
        const val TAG = "FlowVoiceSecrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val RETRY_DELAY_MS = 50L
        const val LEGACY_PREFS_NAME = "flowvoice_secrets"
        const val LEGACY_KEY_OPENROUTER = "openrouter_api_key"
        const val LEGACY_MASTER_KEY_ALIAS = "_androidx_security_master_key_"
        const val LEGACY_KEY_KEYSET = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        const val LEGACY_VALUE_KEYSET = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
    }
}
