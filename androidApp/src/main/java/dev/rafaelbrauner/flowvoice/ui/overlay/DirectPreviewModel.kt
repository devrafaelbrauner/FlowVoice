package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DirectInsertionProgress

enum class DirectPhase { Starting, Recording, Finishing, Ready }

sealed interface DirectPreviewState {
    data object Hidden : DirectPreviewState

    data class Live(
        val phase: DirectPhase,
        val clock: String,
        val status: String,
        val typedTail: String,
        val pending: String,
        val transcribing: Boolean,
        val notice: String?,
        val warning: String?,
        val canInsertHere: Boolean,
        val cancelLabel: String
    ) : DirectPreviewState

    data class Result(val success: Boolean, val message: String, val detail: String? = null) : DirectPreviewState
}

// Prévia colada à bolha na sessão direta (P139): "no campo" é o que já foi digitado (o Cancelar não
// apaga), "transcrevendo…" é a janela em andamento e "pendente" o que ficou fora do campo depois de uma pausa.
object DirectPreviewModel {
    const val STATUS_STARTING = "iniciando"
    const val STATUS_LISTENING = "ouvindo · toque na bolha para encerrar"
    const val STATUS_PAUSED = "pausado · nada mais é digitado sozinho"
    const val STATUS_FINISHING = "finalizando"
    const val STATUS_PROOFREADING = "revisando…"
    const val STATUS_NOT_TYPED = "não digitado"
    const val TRANSCRIBING = "transcrevendo…"
    const val TYPED_LABEL = "no campo"
    const val PENDING_LABEL = "pendente"
    const val CANCEL = "Cancelar"
    const val DISCARD = "Descartar"
    const val INSERT_HERE = "Inserir aqui"
    const val NOTHING_TRANSCRIBED = "Nada transcrito."
    const val FAILED = "Falha no ditado"
    private const val TAIL_CHARS = 90
    private const val ELLIPSIS = "…"

    fun from(
        status: DictationPipelineStatus,
        progress: DirectInsertionProgress,
        transcribing: Boolean,
        elapsedMs: Long,
        owned: Boolean,
        dismissed: Boolean
    ): DirectPreviewState {
        if (!owned) return DirectPreviewState.Hidden
        val clock = DictationBarModel.clock(elapsedMs)
        return when (status) {
            DictationPipelineStatus.Idle,
            DictationPipelineStatus.Cancelled -> DirectPreviewState.Hidden
            DictationPipelineStatus.Starting -> live(DirectPhase.Starting, clock, STATUS_STARTING, progress, transcribing = false)
            DictationPipelineStatus.Recording -> live(
                DirectPhase.Recording,
                clock,
                if (progress.paused) STATUS_PAUSED else STATUS_LISTENING,
                progress,
                transcribing
            )
            DictationPipelineStatus.Transcribing -> live(
                DirectPhase.Finishing,
                clock,
                if (progress.proofreading) STATUS_PROOFREADING else STATUS_FINISHING,
                progress,
                transcribing
            )
            is DictationPipelineStatus.Ready -> live(
                DirectPhase.Ready,
                clock,
                STATUS_NOT_TYPED,
                progress.copy(
                    pending = status.text,
                    pausedReason = status.refusal ?: progress.pausedReason,
                    warning = status.warning ?: progress.warning
                ),
                transcribing = false,
                cancelLabel = DISCARD
            )
            is DictationPipelineStatus.Completed -> when {
                dismissed -> DirectPreviewState.Hidden
                status.text.isNotBlank() -> DirectPreviewState.Result(
                    success = true,
                    message = "Digitado no campo · ${words(status.text)}",
                    detail = status.warning
                )
                else -> DirectPreviewState.Result(success = false, message = NOTHING_TRANSCRIBED, detail = status.warning)
            }
            is DictationPipelineStatus.Failed ->
                if (dismissed) DirectPreviewState.Hidden else DirectPreviewState.Result(false, FAILED, status.message)
        }
    }

    fun tail(text: String, maxChars: Int = TAIL_CHARS): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        val end = trimmed.takeLast(maxChars)
        val startsAtWord = trimmed[trimmed.length - maxChars - 1].isWhitespace()
        val space = end.indexOfFirst { it.isWhitespace() }
        val cut = if (!startsAtWord && space in 0 until end.length - 1) end.substring(space + 1) else end
        return ELLIPSIS + cut.trimStart()
    }

    private fun words(text: String): String {
        val count = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return if (count == 1) "1 palavra" else "$count palavras"
    }

    private fun live(
        phase: DirectPhase,
        clock: String,
        status: String,
        progress: DirectInsertionProgress,
        transcribing: Boolean,
        cancelLabel: String = CANCEL
    ) = DirectPreviewState.Live(
        phase = phase,
        clock = clock,
        status = status,
        typedTail = tail(progress.typed),
        pending = progress.pending.trim(),
        transcribing = transcribing,
        notice = progress.pausedReason,
        warning = progress.warning,
        canInsertHere = progress.pending.isNotBlank(),
        cancelLabel = cancelLabel
    )
}
