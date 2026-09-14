package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayStartRequests {
    const val ACTION_START_DICTATION = "dev.rafaelbrauner.flowvoice.action.START_DICTATION"

    private val requested = MutableStateFlow(0)
    private var handled = 0

    val count: StateFlow<Int> = requested.asStateFlow()

    fun request() {
        requested.value += 1
    }

    fun takePending(): Boolean {
        val current = requested.value
        if (current <= handled) return false
        handled = current
        return true
    }
}
