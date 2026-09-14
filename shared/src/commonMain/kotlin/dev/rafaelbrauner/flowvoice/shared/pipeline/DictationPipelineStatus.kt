package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult

sealed interface DictationPipelineStatus {
    val isBusy: Boolean
        get() = this is Starting || this is Recording || this is Transcribing || this is Ready

    data object Idle : DictationPipelineStatus

    data object Starting : DictationPipelineStatus

    data object Recording : DictationPipelineStatus

    data object Transcribing : DictationPipelineStatus

    data class Ready(
        val text: String,
        val warning: String? = null,
        val latencyMs: Long? = null,
        val refusal: String? = null
    ) : DictationPipelineStatus

    data class Completed(
        val text: String,
        val insertion: TextInsertionResult,
        val warning: String? = null,
        val latencyMs: Long? = null
    ) : DictationPipelineStatus

    data object Cancelled : DictationPipelineStatus

    data class Failed(val message: String) : DictationPipelineStatus
}

data class DictationPipelineSession(
    val id: Int,
    val target: DictationTarget,
    val status: DictationPipelineStatus
)
