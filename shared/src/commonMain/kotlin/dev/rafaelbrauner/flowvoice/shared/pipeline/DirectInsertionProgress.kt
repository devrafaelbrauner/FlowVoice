package dev.rafaelbrauner.flowvoice.shared.pipeline

// Andamento da sessão direta (P139): o que já está no campo, o que foi transcrito e não digitado
// (só depois de uma pausa) e o motivo da pausa.
data class DirectInsertionProgress(
    val sessionId: Int = 0,
    val active: Boolean = false,
    val typed: String = "",
    val pending: String = "",
    val pausedReason: String? = null,
    val warning: String? = null,
    // Revisão final do ditado em andamento (P147): a prévia mostra "revisando…" enquanto isso.
    val proofreading: Boolean = false
) {
    val paused: Boolean
        get() = pausedReason != null
}
