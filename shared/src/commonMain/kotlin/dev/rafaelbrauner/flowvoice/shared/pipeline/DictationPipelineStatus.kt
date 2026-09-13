package dev.rafaelbrauner.flowvoice.shared.pipeline

sealed interface DictationPipelineStatus {
    val isBusy: Boolean
        get() = this is Recording || this is Transcribing

    data object Idle : DictationPipelineStatus

    data object Recording : DictationPipelineStatus

    data object Transcribing : DictationPipelineStatus

    data class Completed(
        val text: String,
        val insertion: InsertOutcome
    ) : DictationPipelineStatus

    data object Cancelled : DictationPipelineStatus

    data class Failed(val message: String) : DictationPipelineStatus
}
