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
        // Texto do motor no aparelho ainda não fechado em pedaço: provisório, nunca digitado.
        val live: String,
        val notice: String?,
        val warning: String?,
        val canInsertHere: Boolean,
        // Nulo: sem botão. Depois do toque de parar não há o que cancelar (o texto já entrou ou está a
        // caminho, e a passada final só o troca pelo certo).
        val cancelLabel: String?
    ) : DirectPreviewState

    data class Result(val success: Boolean, val message: String, val detail: String? = null) : DirectPreviewState

    // Aviso curto, sem cabeçalho nem botões: some sozinho em alguns segundos ou com um toque. Só existe
    // quando o texto que ficou está incompleto de fato (decisão de 2026-09-28).
    data class Notice(val message: String) : DirectPreviewState
}

// Prévia colada à bolha na sessão direta (P139): "no campo" é o que já foi digitado (o Cancelar não
// apaga), "transcrevendo…" é a janela em andamento e "pendente" o que ficou fora do campo depois de uma pausa.
object DirectPreviewModel {
    const val STATUS_STARTING = "iniciando"
    const val STATUS_LISTENING = "ouvindo · toque na bolha para encerrar"
    // Sem a bolha na tela, o encerrar mora no próprio cartão (P159).
    const val STATUS_LISTENING_WITHOUT_BUBBLE = "ouvindo · encerre aqui embaixo"
    const val STATUS_PAUSED = "pausado · nada mais é digitado sozinho"
    const val STATUS_FINISHING = "finalizando"
    const val STATUS_PROOFREADING = "revisando…"
    const val STATUS_NOT_TYPED = "não digitado"
    const val TRANSCRIBING = "transcrevendo…"
    const val TYPED_LABEL = "no campo"
    const val PENDING_LABEL = "pendente"
    const val CANCEL = "Cancelar"
    const val FINISH = "Encerrar"
    const val DISCARD = "Descartar"
    const val INSERT_HERE = "Inserir aqui"
    const val FAILED = "Falha no ditado"
    private const val TAIL_CHARS = 90
    private const val ELLIPSIS = "…"

    fun from(
        status: DictationPipelineStatus,
        progress: DirectInsertionProgress,
        transcribing: Boolean,
        elapsedMs: Long,
        owned: Boolean,
        dismissed: Boolean,
        bubbleHidden: Boolean = false,
        live: String = ""
    ): DirectPreviewState {
        if (!owned) return DirectPreviewState.Hidden
        val clock = DictationBarModel.clock(elapsedMs)
        // No ditado normal o texto está no próprio campo e a bolha encerra: o cartão cobria a linha em que
        // se dita (S26, 2026-09-28). Ele volta para o que só ele mostra: pausa com pendente, "revisando…" e
        // o encerrar quando a bolha está oculta (P159). Um trecho que falhou sozinho vira só o aviso curto.
        val quiet = !progress.paused && !bubbleHidden && progress.pending.isBlank() &&
            progress.pausedReason == null && !progress.proofreading
        val idle = progress.warning?.let { DirectPreviewState.Notice(it) } ?: DirectPreviewState.Hidden
        return when (status) {
            DictationPipelineStatus.Idle,
            DictationPipelineStatus.Cancelled -> DirectPreviewState.Hidden
            DictationPipelineStatus.Starting -> if (quiet) idle else live(DirectPhase.Starting, clock, STATUS_STARTING, progress, transcribing = false, live = live)
            DictationPipelineStatus.Recording -> if (quiet) idle else live(
                DirectPhase.Recording,
                clock,
                when {
                    progress.paused -> STATUS_PAUSED
                    bubbleHidden -> STATUS_LISTENING_WITHOUT_BUBBLE
                    else -> STATUS_LISTENING
                },
                progress,
                transcribing,
                live = live
            )
            DictationPipelineStatus.Transcribing -> if (quiet) idle else live(
                DirectPhase.Finishing,
                clock,
                if (progress.proofreading) STATUS_PROOFREADING else STATUS_FINISHING,
                progress,
                transcribing,
                cancelLabel = null,
                live = live
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
            // O texto já está no campo, e ditado sem texto não perdeu nada: a bolha só volta ao repouso. O
            // cartão fica só com o aviso de que algo faltou no que ficou.
            is DictationPipelineStatus.Completed ->
                if (dismissed) DirectPreviewState.Hidden else status.warning?.let { DirectPreviewState.Notice(it) } ?: DirectPreviewState.Hidden
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

    private fun live(
        phase: DirectPhase,
        clock: String,
        status: String,
        progress: DirectInsertionProgress,
        transcribing: Boolean,
        cancelLabel: String? = CANCEL,
        live: String = ""
    ) = DirectPreviewState.Live(
        phase = phase,
        clock = clock,
        status = status,
        typedTail = tail(progress.typed),
        pending = progress.pending.trim(),
        transcribing = transcribing,
        live = tail(live),
        notice = progress.pausedReason,
        warning = progress.warning,
        canInsertHere = progress.pending.isNotBlank(),
        cancelLabel = cancelLabel
    )
}
