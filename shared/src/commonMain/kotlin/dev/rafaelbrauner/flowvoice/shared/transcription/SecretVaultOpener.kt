package dev.rafaelbrauner.flowvoice.shared.transcription

class SecretVaultOpener<T : Any>(
    private val open: () -> T,
    private val deleteVault: () -> Unit,
    private val isCorruption: (Throwable) -> Boolean,
    private val pause: () -> Unit,
    private val attempts: Int = DEFAULT_ATTEMPTS
) {
    sealed interface Outcome<out T> {
        data class Opened<T>(val vault: T, val recreated: Boolean) : Outcome<T>
        data class Unavailable(val cause: Exception) : Outcome<Nothing>
    }

    fun openVault(): Outcome<T> {
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            if (attempt > 0) pause()
            try {
                return Outcome.Opened(open(), recreated = false)
            } catch (error: Exception) {
                lastError = error
            }
        }
        val failure = checkNotNull(lastError)
        if (!isCorruption(failure)) return Outcome.Unavailable(failure)
        deleteVault()
        return try {
            Outcome.Opened(open(), recreated = true)
        } catch (error: Exception) {
            Outcome.Unavailable(error)
        }
    }

    companion object {
        const val DEFAULT_ATTEMPTS = 2
    }
}
