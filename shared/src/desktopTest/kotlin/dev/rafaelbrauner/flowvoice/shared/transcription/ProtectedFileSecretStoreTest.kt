package dev.rafaelbrauner.flowvoice.shared.transcription

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProtectedFileSecretStoreTest {
    private val key = "sk-or-v1-testkey123456"

    private class XorCipher : SecretCipher {
        override fun protect(plainText: ByteArray): ByteArray =
            plainText.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun unprotect(cipherText: ByteArray): ByteArray = protect(cipherText)
    }

    private class BrokenCipher : SecretCipher {
        override fun protect(plainText: ByteArray): ByteArray = plainText.reversedArray()

        override fun unprotect(cipherText: ByteArray): ByteArray = error("wrong user")
    }

    private fun tempFile(): Path = Files.createTempDirectory("flowvoice-secrets").resolve("nested/openrouter.key")

    @Test
    fun roundTripTrimsKeyAndNeverWritesPlainText() {
        val file = tempFile()
        val store = ProtectedFileSecretStore(file, XorCipher())

        store.writeOpenRouterKey("  $key  ")

        val stored = Files.readAllBytes(file).toString(Charsets.ISO_8859_1)
        assertFalse(stored.contains(key))
        assertFalse(stored.contains("sk-or"))
        assertEquals(key, store.readOpenRouterKey())
    }

    @Test
    fun overwriteReplacesPreviousKey() {
        val store = ProtectedFileSecretStore(tempFile(), XorCipher())

        store.writeOpenRouterKey(key)
        store.writeOpenRouterKey("sk-or-v1-otherkey987654")

        assertEquals("sk-or-v1-otherkey987654", store.readOpenRouterKey())
    }

    @Test
    fun clearRemovesStoredKey() {
        val file = tempFile()
        val store = ProtectedFileSecretStore(file, XorCipher())
        store.writeOpenRouterKey(key)

        store.clearOpenRouterKey()

        assertFalse(Files.exists(file))
        assertNull(store.readOpenRouterKey())
    }

    @Test
    fun blankKeyIsRejected() {
        val store = ProtectedFileSecretStore(tempFile(), XorCipher())

        assertFailsWith<IllegalArgumentException> { store.writeOpenRouterKey("   ") }
    }

    @Test
    fun missingOrUndecryptableKeyReadsAsAbsent() {
        assertNull(ProtectedFileSecretStore(tempFile(), XorCipher()).readOpenRouterKey())

        val file = tempFile()
        val store = ProtectedFileSecretStore(file, BrokenCipher())
        store.writeOpenRouterKey(key)
        assertNull(store.readOpenRouterKey())
    }

    @Test
    fun unavailableStoreRefusesToSave() {
        val store = UnavailableSecretStore("sem cofre")

        assertFailsWith<SecretStoreUnavailableException> { store.writeOpenRouterKey(key) }
        assertNull(store.readOpenRouterKey())
    }
}
