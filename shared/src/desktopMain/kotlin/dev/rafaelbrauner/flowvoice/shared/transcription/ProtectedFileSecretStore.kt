package dev.rafaelbrauner.flowvoice.shared.transcription

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

interface SecretCipher {
    fun protect(plainText: ByteArray): ByteArray

    fun unprotect(cipherText: ByteArray): ByteArray
}

class ProtectedFileSecretStore(
    private val file: Path,
    private val cipher: SecretCipher
) : SecretStore {

    override fun readOpenRouterKey(): String? {
        if (!Files.isRegularFile(file)) return null
        val plain = try {
            cipher.unprotect(Files.readAllBytes(file))
        } catch (error: Exception) {
            System.err.println("$TAG: stored OpenRouter key could not be read (${error::class.simpleName})")
            return null
        }
        return try {
            plain.toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } finally {
            plain.fill(0)
        }
    }

    override fun writeOpenRouterKey(value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "key must not be blank" }
        val plain = trimmed.toByteArray(Charsets.UTF_8)
        val cipherBytes = try {
            cipher.protect(plain)
        } finally {
            plain.fill(0)
        }
        val directory = file.toAbsolutePath().parent
        Files.createDirectories(directory)
        val temp = Files.createTempFile(directory, file.fileName.toString(), ".tmp")
        try {
            Files.write(temp, cipherBytes)
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun clearOpenRouterKey() {
        Files.deleteIfExists(file)
    }

    private companion object {
        private const val TAG = "FlowVoiceSecrets"
    }
}

class SecretStoreUnavailableException(message: String) : IllegalStateException(message)

class UnavailableSecretStore(private val reason: String) : SecretStore {
    override fun readOpenRouterKey(): String? = null

    override fun writeOpenRouterKey(value: String) {
        throw SecretStoreUnavailableException(reason)
    }

    override fun clearOpenRouterKey() = Unit
}
