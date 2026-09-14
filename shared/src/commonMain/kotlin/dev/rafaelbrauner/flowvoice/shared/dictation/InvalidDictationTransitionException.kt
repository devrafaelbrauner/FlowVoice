package dev.rafaelbrauner.flowvoice.shared.dictation

class InvalidDictationTransitionException(
    from: DictationSessionState,
    to: DictationSessionState
) : IllegalStateException("Invalid dictation transition: $from -> $to")