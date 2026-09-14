package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LegacySecretMigrationTest {

    @Test
    fun migratesKeyAndDeletesLegacyOnlyAfterTheNewWriteIsConfirmed() {
        val env = Env(legacyKey = "sk-or-v1-legacy")

        val outcome = env.migration.migrate()

        assertIs<LegacySecretMigration.Outcome.Migrated>(outcome)
        assertEquals("sk-or-v1-legacy", env.target.key)
        assertEquals(listOf("write", "read", "delete"), env.events.filter { it != "read-before" })
    }

    @Test
    fun failureWritingTheNewVaultKeepsTheLegacyFile() {
        val env = Env(legacyKey = "sk-or-v1-legacy")
        env.target.writeFailure = SecretStoreUnavailableException("keystore down")

        val outcome = env.migration.migrate()

        val kept = assertIs<LegacySecretMigration.Outcome.Kept>(outcome)
        assertIs<SecretStoreUnavailableException>(kept.cause)
        assertEquals(0, env.deletions)
    }

    @Test
    fun unreadableLegacyVaultIsNotDeleted() {
        val env = Env(legacyFailure = IllegalStateException("legacy keyset unreadable"))

        val outcome = env.migration.migrate()

        assertIs<LegacySecretMigration.Outcome.Kept>(outcome)
        assertEquals(0, env.deletions)
        assertNull(env.target.key)
    }

    @Test
    fun newWriteThatCannotBeReadBackKeepsTheLegacyFile() {
        val env = Env(legacyKey = "sk-or-v1-legacy")
        env.target.dropWrites = true

        val outcome = env.migration.migrate()

        assertIs<LegacySecretMigration.Outcome.Kept>(outcome)
        assertEquals(0, env.deletions)
    }

    @Test
    fun missingLegacyFileDoesNothing() {
        val env = Env(legacyExists = false)

        val outcome = env.migration.migrate()

        assertIs<LegacySecretMigration.Outcome.NoLegacy>(outcome)
        assertEquals(0, env.deletions)
        assertEquals(0, env.legacyReads)
    }

    @Test
    fun readableLegacyWithoutKeyIsRemoved() {
        val env = Env(legacyKey = null)

        val outcome = env.migration.migrate()

        assertIs<LegacySecretMigration.Outcome.LegacyEmptyRemoved>(outcome)
        assertEquals(1, env.deletions)
    }

    @Test
    fun keyAlreadyInTheNewVaultWinsAndLegacyIsRemoved() {
        val env = Env(legacyKey = "sk-or-v1-legacy")
        env.target.key = "sk-or-v1-current"

        val outcome = env.migration.migrate()

        assertIs<LegacySecretMigration.Outcome.AlreadyMigrated>(outcome)
        assertEquals("sk-or-v1-current", env.target.key)
        assertEquals(1, env.deletions)
    }

    private class RecordingStore(private val events: MutableList<String>) : SecretStore {
        var key: String? = null
        var writeFailure: Exception? = null
        var dropWrites = false
        private var wrote = false

        override fun readOpenRouterKey(): String? {
            events += if (wrote) "read" else "read-before"
            return key
        }

        override fun writeOpenRouterKey(value: String) {
            events += "write"
            writeFailure?.let { throw it }
            wrote = true
            if (!dropWrites) key = value.trim()
        }

        override fun clearOpenRouterKey() {
            key = null
        }
    }

    private class Env(
        legacyExists: Boolean = true,
        legacyKey: String? = null,
        legacyFailure: Exception? = null
    ) {
        val events = mutableListOf<String>()
        val target = RecordingStore(events)
        var deletions = 0
        var legacyReads = 0

        val migration = LegacySecretMigration(
            legacyExists = { legacyExists },
            readLegacy = {
                legacyReads++
                legacyFailure?.let { throw it }
                legacyKey
            },
            target = target,
            deleteLegacy = {
                events += "delete"
                deletions++
            }
        )
    }
}
