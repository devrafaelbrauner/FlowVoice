package dev.rafaelbrauner.flowvoice.shared.dictation

// O que fazer com o retorno de uma leitura da captura (P154).
sealed interface CaptureReadAction {
    // Veio áudio: entregar o frame.
    data object Deliver : CaptureReadAction

    // A captura já não está ativa; a leitura vazia é o encerramento normal destravando o `read`.
    data object Stop : CaptureReadAction

    // Nada agora, mas ainda dentro da tolerância: esperar um pouco e ler de novo.
    data class WaitAndRetry(val pauseMs: Long) : CaptureReadAction

    // Última tentativa antes de desistir: reabrir a captura mantendo a sessão de ditado viva.
    data object Restart : CaptureReadAction

    // Fim de linha, sempre com motivo: quem lê o log precisa saber por que o ditado terminou.
    data class GiveUp(val reason: String) : CaptureReadAction
}

// P154: `AudioRecord.read` devolveu `code=0` e o ditado morreu no meio, três vezes em três baterias
// no S26 (2026-09-16). Zero byte não é erro documentado nem fim de fluxo: é buffer vazio. Esta é a
// decisão pura — tolerar por uma janela de tempo, reabrir uma vez, e só então desistir.
//
// A tolerância é em tempo, não em contagem: uma leitura vazia pode voltar na hora (laço rápido) ou
// depois de 100 ms (leitura bloqueante), e o que importa é há quanto tempo o microfone está mudo.
class CaptureReadPolicy(
    private val emptyToleranceMs: Long = DEFAULT_EMPTY_TOLERANCE_MS,
    private val retryPauseMs: Long = DEFAULT_RETRY_PAUSE_MS,
    private val maxRestarts: Int = DEFAULT_MAX_RESTARTS
) {
    private var emptySinceMs: Long? = null

    var consecutiveEmptyReads: Int = 0
        private set

    var restartCount: Int = 0
        private set

    init {
        require(emptyToleranceMs > 0L) { "emptyToleranceMs must be positive" }
        require(retryPauseMs >= 0L) { "retryPauseMs must not be negative" }
        require(maxRestarts >= 0) { "maxRestarts must not be negative" }
    }

    fun onRead(bytesRead: Int, captureActive: Boolean, nowMs: Long): CaptureReadAction {
        if (bytesRead > 0) {
            clearEmptyRun()
            return CaptureReadAction.Deliver
        }
        if (!captureActive) {
            clearEmptyRun()
            return CaptureReadAction.Stop
        }
        return if (bytesRead == 0) onEmptyRead(nowMs) else onErrorCode(bytesRead)
    }

    private fun onEmptyRead(nowMs: Long): CaptureReadAction {
        consecutiveEmptyReads++
        val since = emptySinceMs ?: nowMs.also { emptySinceMs = it }
        val silentForMs = nowMs - since
        if (silentForMs < emptyToleranceMs) {
            return CaptureReadAction.WaitAndRetry(retryPauseMs)
        }
        if (restartCount < maxRestarts) {
            restartCount++
            clearEmptyRun()
            return CaptureReadAction.Restart
        }
        return CaptureReadAction.GiveUp(
            "leitura vazia por ${silentForMs} ms apos $restartCount reabertura(s)"
        )
    }

    private fun onErrorCode(code: Int): CaptureReadAction {
        clearEmptyRun()
        // Objeto morto é exatamente o caso que reabrir conserta; os outros códigos são de parâmetro
        // ou de estado e não melhoram tentando de novo.
        if (code == ERROR_DEAD_OBJECT && restartCount < maxRestarts) {
            restartCount++
            return CaptureReadAction.Restart
        }
        return CaptureReadAction.GiveUp("leitura devolveu code=$code apos $restartCount reabertura(s)")
    }

    private fun clearEmptyRun() {
        emptySinceMs = null
        consecutiveEmptyReads = 0
    }

    companion object {
        // Códigos documentados do AudioRecord.read; `0` não é um deles, por isso a tolerância.
        const val ERROR = -1
        const val ERROR_BAD_VALUE = -2
        const val ERROR_INVALID_OPERATION = -3
        const val ERROR_DEAD_OBJECT = -6

        // Três frames de 100 ms (CaptureFrames.FRAME_DURATION_MS). Uma leitura saudável volta a cada
        // ~100 ms; ficar 300 ms mudo já é anormal, mas ainda é menos do que se perde hoje, que é a
        // sessão inteira. Com uma reabertura, o pior caso vira ~600 ms antes de desistir.
        const val DEFAULT_EMPTY_TOLERANCE_MS = 300L

        // Pausa entre releituras vazias, para não girar a CPU quando o `read` volta na hora.
        const val DEFAULT_RETRY_PAUSE_MS = 20L

        const val DEFAULT_MAX_RESTARTS = 1
    }
}
