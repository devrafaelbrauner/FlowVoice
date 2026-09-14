package dev.rafaelbrauner.flowvoice.shared.transcription

import android.content.Context
import android.util.Base64
import java.io.IOException

internal class PrefsVaultStorage(context: Context) : VaultStorage {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): SealedSecret? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val cipherText = prefs.getString(KEY_CIPHER_TEXT, null) ?: return null
        return SealedSecret(
            iv = Base64.decode(iv, Base64.NO_WRAP),
            cipherText = Base64.decode(cipherText, Base64.NO_WRAP)
        )
    }

    override fun save(sealed: SealedSecret) {
        val written = prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(sealed.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHER_TEXT, Base64.encodeToString(sealed.cipherText, Base64.NO_WRAP))
            .commit()
        if (!written) throw IOException("cofre da chave não gravado")
    }

    override fun clear() {
        val cleared = prefs.edit().remove(KEY_IV).remove(KEY_CIPHER_TEXT).commit()
        if (!cleared) throw IOException("cofre da chave não limpo")
    }

    companion object {
        const val PREFS_NAME = "flowvoice_vault"
        private const val KEY_IV = "openrouter_key_iv"
        private const val KEY_CIPHER_TEXT = "openrouter_key_ct"
    }
}
