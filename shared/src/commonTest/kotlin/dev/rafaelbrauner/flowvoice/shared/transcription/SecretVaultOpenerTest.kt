package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SecretVaultOpenerTest {

    @Test
    fun unusableMasterKeyKeepsVaultFileAndReportsUnavailable() {
        val env = Env(outcomes = List(3) { Result.failure(MasterKeyUnusable()) })

        val outcome = env.opener.openVault()

        val unavailable = assertIs<SecretVaultOpener.Outcome.Unavailable>(outcome)
        assertIs<MasterKeyUnusable>(unavailable.cause)
        assertEquals(0, env.deletions)
        assertEquals(2, env.openCalls)
    }

    @Test
    fun transientFailureFollowedBySuccessDoesNotDeleteVault() {
        val env = Env(outcomes = listOf(Result.failure(KeysetCorrupted()), Result.success(VAULT)))

        val outcome = env.opener.openVault()

        val opened = assertIs<SecretVaultOpener.Outcome.Opened<String>>(outcome)
        assertSame(VAULT, opened.vault)
        assertFalse(opened.recreated)
        assertEquals(0, env.deletions)
        assertEquals(1, env.pauses)
    }

    @Test
    fun corruptedKeysetIsDeletedOnceAndReopened() {
        val env = Env(
            outcomes = listOf(
                Result.failure(KeysetCorrupted()),
                Result.failure(KeysetCorrupted()),
                Result.success(VAULT)
            )
        )

        val outcome = env.opener.openVault()

        val opened = assertIs<SecretVaultOpener.Outcome.Opened<String>>(outcome)
        assertTrue(opened.recreated)
        assertEquals(1, env.deletions)
    }

    @Test
    fun corruptedKeysetThatStillFailsAfterDeletionReportsUnavailable() {
        val env = Env(outcomes = List(3) { Result.failure(KeysetCorrupted()) })

        val outcome = env.opener.openVault()

        assertIs<SecretVaultOpener.Outcome.Unavailable>(outcome)
        assertEquals(1, env.deletions)
    }

    @Test
    fun providerRuntimeExceptionDoesNotPropagate() {
        val env = Env(outcomes = List(3) { Result.failure(ProviderFailure()) })

        val outcome = env.opener.openVault()

        assertIs<SecretVaultOpener.Outcome.Unavailable>(outcome)
        assertEquals(0, env.deletions)
    }

    private class MasterKeyUnusable : Exception("master key unusable")
    private class KeysetCorrupted : Exception("keyset corrupted")
    private class ProviderFailure : RuntimeException("provider failure")

    private class Env(outcomes: List<Result<String>>) {
        private val pending = ArrayDeque(outcomes)
        var openCalls = 0
        var deletions = 0
        var pauses = 0

        val opener = SecretVaultOpener(
            open = {
                openCalls++
                pending.removeFirst().getOrThrow()
            },
            deleteVault = { deletions++ },
            isCorruption = { it is KeysetCorrupted },
            pause = { pauses++ }
        )
    }

    private companion object {
        const val VAULT = "vault"
    }
}
