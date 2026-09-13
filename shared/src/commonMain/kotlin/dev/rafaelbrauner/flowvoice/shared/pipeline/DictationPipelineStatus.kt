package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult

sealed interface DictationPipelineStatus {
    val isBusy: Boolean
        get() = this is Recording || this is Transcribing

    data object Idle : DictationPipelineStatus

    data object Recording : DictationPipelineStatus

    data object Transcribing : DictationPipelineStatus

    data class Completed(
        val text: String,
        val insertion: TextInsertionResult
    ) : DictationPipelineStatus

    data object Cancelled : DictationPipelineStatus

    data class Failed(val message: String) : DictationPipelineStatus
}
