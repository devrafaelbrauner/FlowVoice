package dev.rafaelbrauner.flowvoice.shared.dictation

class DictationSession {
    var state: DictationSessionState = DictationSessionState.Idle
        private set

    fun start() {
        transitionTo(DictationSessionState.Capturing)
    }

    fun requestFinalization() {
        transitionTo(DictationSessionState.Finalizing)
    }

    fun completeFinalization() {
        transitionTo(DictationSessionState.Finalized)
    }

    fun cancel() {
        transitionTo(DictationSessionState.Cancelled)
    }

    fun fail(message: String) {
        transitionTo(DictationSessionState.Error(message))
    }

    fun reset() {
        transitionTo(DictationSessionState.Idle)
    }

    private fun transitionTo(next: DictationSessionState) {
        if (!state.allows(next)) {
            throw InvalidDictationTransitionException(state, next)
        }
        state = next
    }
}

private fun DictationSessionState.allows(next: DictationSessionState): Boolean = when (this) {
    is DictationSessionState.Idle ->
        next is DictationSessionState.Capturing || next is DictationSessionState.Error
    is DictationSessionState.Capturing ->
        next is DictationSessionState.Finalizing ||
            next is DictationSessionState.Cancelled ||
            next is DictationSessionState.Error
    is DictationSessionState.Finalizing ->
        next is DictationSessionState.Finalized ||
            next is DictationSessionState.Cancelled ||
            next is DictationSessionState.Error
    is DictationSessionState.Finalized,
    is DictationSessionState.Cancelled,
    is DictationSessionState.Error -> next is DictationSessionState.Idle
}