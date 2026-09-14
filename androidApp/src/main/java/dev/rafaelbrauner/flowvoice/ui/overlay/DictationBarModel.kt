package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreview

enum class OverlayMode { Bubble, Bar }

enum class BarPhase { Starting, Recording, Transcribing, Ready }

sealed interface DictationBarState {
    data object Hidden : DictationBarState

    data class Live(
        val phase: BarPhase,
        val finalized: String,
        val provisional: String,
        val clock: String,
        val route: String,
        val canInsert: Boolean,
        val warning: String? = null
    ) : DictationBarState

    data class Result(
        val success: Boolean,
        val message: String,
        val detail: String? = null
    ) : DictationBarState
}

object DictationBarModel {
    const val ROUTE_STARTING = "iniciando"
    const val ROUTE_LISTENING = "ouvindo"
    const val ROUTE_TRANSCRIBING = "transcrevendo"
    const val ROUTE_PROOFREADING = "revisando pontuação · IA"
    const val ROUTE_READY = "transcrição concluída"
    const val NOTHING_TRANSCRIBED = "Nada transcrito."

    fun from(
        status: DictationPipelineStatus,
        live: LivePreview,
        elapsedMs: Long,
        proofreadingEnabled: Boolean,
        owned: Boolean,
        dismissed: Boolean
    ): DictationBarState {
        if (!owned) return DictationBarState.Hidden
        val clock = clock(elapsedMs)
        return when (status) {
            DictationPipelineStatus.Idle,
            DictationPipelineStatus.Cancelled -> DictationBarState.Hidden
            DictationPipelineStatus.Starting -> DictationBarState.Live(
                phase = BarPhase.Starting,
                finalized = "",
                provisional = "",
                clock = clock,
                route = ROUTE_STARTING,
                canInsert = false
            )
            DictationPipelineStatus.Recording -> DictationBarState.Live(
                phase = BarPhase.Recording,
                finalized = live.finalized,
                provisional = live.provisional,
                clock = clock,
                route = ROUTE_LISTENING,
                canInsert = true
            )
            DictationPipelineStatus.Transcribing -> DictationBarState.Live(
                phase = BarPhase.Transcribing,
                finalized = live.finalized,
                provisional = live.provisional,
                clock = clock,
                route = if (proofreadingEnabled) ROUTE_PROOFREADING else ROUTE_TRANSCRIBING,
                canInsert = false
            )
            is DictationPipelineStatus.Ready -> DictationBarState.Live(
                phase = BarPhase.Ready,
                finalized = status.text,
                provisional = "",
                clock = clock,
                route = ROUTE_READY,
                canInsert = status.text.isNotBlank(),
                warning = status.warning ?: NOTHING_TRANSCRIBED.takeIf { status.text.isBlank() }
            )
            is DictationPipelineStatus.Completed -> when {
                dismissed -> DictationBarState.Hidden
                status.insertion.success -> DictationBarState.Result(
                    success = true,
                    message = listOfNotNull("Inserido no campo ativo", status.latencyMs?.let(::latency))
                        .joinToString(" · "),
                    detail = status.warning
                )
                else -> DictationBarState.Result(
                    success = false,
                    message = "Texto não inserido",
                    detail = status.insertion.message
                )
            }
            is DictationPipelineStatus.Failed ->
                if (dismissed) {
                    DictationBarState.Hidden
                } else {
                    DictationBarState.Result(success = false, message = "Falha no ditado", detail = status.message)
                }
        }
    }

    fun clock(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs.coerceAtLeast(0L) / 1_000L)
        val seconds = totalSeconds % 60
        return "${totalSeconds / 60}:${if (seconds < 10) "0" else ""}$seconds"
    }

    fun latency(latencyMs: Long): String {
        val tenths = (latencyMs.coerceAtLeast(0L) + 50L) / 100L
        return "${tenths / 10},${tenths % 10} s"
    }
}
