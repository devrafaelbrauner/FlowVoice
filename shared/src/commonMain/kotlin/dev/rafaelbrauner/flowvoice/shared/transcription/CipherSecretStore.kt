package dev.rafaelbrauner.flowvoice.shared.transcription

class SealedSecret(val iv: ByteArray, val cipherText: ByteArray)

interface VaultCipher {
    fun seal(plainText: ByteArray): SealedSecret
    fun open(sealed: SealedSecret): ByteArray
}

interface VaultStorage {
    fun load(): SealedSecret?
    fun save(sealed: SealedSecret)
    fun clear()
}

class CipherSecretStore(
    private val cipher: VaultCipher,
    private val storage: VaultStorage,
    private val isCorruption: (Throwable) -> Boolean,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> }
) : SecretStore {

    override fun readOpenRouterKey(): String? {
        val sealed = try {
            storage.load()
        } catch (error: Exception) {
            onFailure("vault_load_failed", error)
            return null
        } ?: return null
        val plain = try {
            cipher.open(sealed)
        } catch (error: Exception) {
            if (isCorruption(error)) {
                clearQuietly()
                onFailure("vault_corrupted_cleared", error)
            } else {
                onFailure("vault_unavailable", error)
            }
            return null
        }
        return try {
            plain.decodeToString().takeIf { it.isNotBlank() }
        } finally {
            plain.fill(0)
        }
    }

    override fun writeOpenRouterKey(value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "key must not be blank" }
        val plain = trimmed.encodeToByteArray()
        val sealed = try {
            cipher.seal(plain)
        } catch (error: Exception) {
            onFailure("vault_seal_failed", error)
            throw SecretStoreUnavailableException(UNAVAILABLE_MESSAGE)
        } finally {
            plain.fill(0)
        }
        try {
            storage.save(sealed)
        } catch (error: Exception) {
            onFailure("vault_save_failed", error)
            throw SecretStoreUnavailableException(UNAVAILABLE_MESSAGE)
        }
    }

    override fun clearOpenRouterKey() {
        clearQuietly()
    }

    private fun clearQuietly() {
        try {
            storage.clear()
        } catch (error: Exception) {
            onFailure("vault_clear_failed", error)
        }
    }

    private companion object {
        const val UNAVAILABLE_MESSAGE = "cofre da chave indisponível neste aparelho"
    }
}
