package dev.rafaelbrauner.flowvoice.shared.transcription

class LegacySecretMigration(
    private val legacyExists: () -> Boolean,
    private val readLegacy: () -> String?,
    private val target: SecretStore,
    private val deleteLegacy: () -> Unit
) {
    sealed interface Outcome {
        data object NoLegacy : Outcome
        data object Migrated : Outcome
        data object AlreadyMigrated : Outcome
        data object LegacyEmptyRemoved : Outcome
        data class Kept(val cause: Exception) : Outcome
    }

    fun migrate(): Outcome {
        if (!legacyExists()) return Outcome.NoLegacy
        val legacyKey = try {
            readLegacy()?.trim()
        } catch (error: Exception) {
            return Outcome.Kept(error)
        }
        if (legacyKey.isNullOrEmpty()) return removeLegacy(Outcome.LegacyEmptyRemoved)
        if (target.readOpenRouterKey() != null) return removeLegacy(Outcome.AlreadyMigrated)
        try {
            target.writeOpenRouterKey(legacyKey)
        } catch (error: Exception) {
            return Outcome.Kept(error)
        }
        if (target.readOpenRouterKey() != legacyKey) {
            return Outcome.Kept(IllegalStateException("a chave migrada não foi confirmada no cofre novo"))
        }
        return removeLegacy(Outcome.Migrated)
    }

    private fun removeLegacy(done: Outcome): Outcome =
        try {
            deleteLegacy()
            done
        } catch (error: Exception) {
            Outcome.Kept(error)
        }
}
