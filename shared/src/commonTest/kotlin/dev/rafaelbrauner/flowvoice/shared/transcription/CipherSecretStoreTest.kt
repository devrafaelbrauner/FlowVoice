package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CipherSecretStoreTest {

    @Test
    fun constructingTheStoreNeverTouchesTheKeystore() {
        val env = Env()

        env.store

        assertEquals(0, env.cipher.calls)
    }

    @Test
    fun writtenKeyIsReadBackAndNeverStoredInClear() {
        val env = Env()

        env.store.writeOpenRouterKey("  sk-or-v1-abcdef  ")

        assertEquals("sk-or-v1-abcdef", env.store.readOpenRouterKey())
        val sealed = assertNotNull(env.storage.sealed)
        assertFalse(sealed.cipherText.decodeToString().contains("sk-or-v1"))
    }

    @Test
    fun keystoreUnavailableOnWriteReportsUnavailableAndKeepsStorageUntouched() {
        val env = Env()
        env.cipher.failure = KeystoreDown()

        assertFailsWith<SecretStoreUnavailableException> { env.store.writeOpenRouterKey("sk-or-v1-abcdef") }

        assertNull(env.storage.sealed)
        assertEquals(0, env.storage.clears)
    }

    @Test
    fun keystoreUnavailableOnReadReturnsNullWithoutDeletingTheSealedKey() {
        val env = Env()
        env.store.writeOpenRouterKey("sk-or-v1-abcdef")
        env.cipher.failure = KeystoreDown()

        assertNull(env.store.readOpenRouterKey())

        assertNotNull(env.storage.sealed)
        assertEquals(0, env.storage.clears)
        env.cipher.failure = null
        assertEquals("sk-or-v1-abcdef", env.store.readOpenRouterKey())
    }

    @Test
    fun corruptedCipherTextIsClearedAndReadReturnsNull() {
        val env = Env()
        env.store.writeOpenRouterKey("sk-or-v1-abcdef")
        env.cipher.failure = CipherTextCorrupted()

        assertNull(env.store.readOpenRouterKey())

        assertNull(env.storage.sealed)
        assertEquals(1, env.storage.clears)
    }

    @Test
    fun blankKeyIsRejectedBeforeReachingTheKeystore() {
        val env = Env()

        assertFailsWith<IllegalArgumentException> { env.store.writeOpenRouterKey("   ") }

        assertEquals(0, env.cipher.calls)
    }

    @Test
    fun clearRemovesTheSealedKey() {
        val env = Env()
        env.store.writeOpenRouterKey("sk-or-v1-abcdef")

        env.store.clearOpenRouterKey()

        assertNull(env.store.readOpenRouterKey())
        assertTrue(env.storage.clears > 0)
    }

    private class KeystoreDown : Exception("keystore down")
    private class CipherTextCorrupted : Exception("bad tag")

    private class FakeCipher : VaultCipher {
        var calls = 0
        var failure: Exception? = null

        override fun seal(plainText: ByteArray): SealedSecret {
            calls++
            failure?.let { throw it }
            return SealedSecret(iv = byteArrayOf(1, 2, 3), cipherText = plainText.map { (it.toInt() xor 0x5A).toByte() }.toByteArray())
        }

        override fun open(sealed: SealedSecret): ByteArray {
            calls++
            failure?.let { throw it }
            return sealed.cipherText.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }

    private class FakeStorage : VaultStorage {
        var sealed: SealedSecret? = null
        var clears = 0

        override fun load(): SealedSecret? = sealed

        override fun save(sealed: SealedSecret) {
            this.sealed = sealed
        }

        override fun clear() {
            clears++
            sealed = null
        }
    }

    private class Env {
        val cipher = FakeCipher()
        val storage = FakeStorage()
        val store = CipherSecretStore(
            cipher = cipher,
            storage = storage,
            isCorruption = { it is CipherTextCorrupted }
        )
    }
}
