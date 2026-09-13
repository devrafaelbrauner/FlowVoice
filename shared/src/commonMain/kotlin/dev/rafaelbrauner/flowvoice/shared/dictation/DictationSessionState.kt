package dev.rafaelbrauner.flowvoice.shared.dictation

sealed interface DictationSessionState {
    val isActive: Boolean
    val isTerminal: Boolean

    data object Idle : DictationSessionState {
        override val isActive = false
        override val isTerminal = false
    }

    data object Capturing : DictationSessionState {
        override val isActive = true
        override val isTerminal = false
    }

    data object Finalizing : DictationSessionState {
        override val isActive = true
        override val isTerminal = false
    }

    data object Finalized : DictationSessionState {
        override val isActive = false
        override val isTerminal = true
    }

    data object Cancelled : DictationSessionState {
        override val isActive = false
        override val isTerminal = true
    }

    data class Error(val message: String) : DictationSessionState {
        override val isActive = false
        override val isTerminal = true
    }
}