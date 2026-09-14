package dev.rafaelbrauner.flowvoice.ui.screens.diagnostics

import dev.rafaelbrauner.flowvoice.ui.components.LOG_BLOCK_MAX_LINES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsLog(
    private val maxLines: Int = LOG_BLOCK_MAX_LINES,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeFormatter: (Long) -> String = ::formatLogTime
) {
    init {
        require(maxLines > 0) { "maxLines must be positive" }
    }

    private val state = MutableStateFlow<List<String>>(emptyList())

    val lines: StateFlow<List<String>> = state.asStateFlow()

    fun add(message: String) {
        val line = "[${timeFormatter(clock())}] $message"
        state.update { current -> (listOf(line) + current).take(maxLines) }
    }

    fun attach(events: Flow<String>, scope: CoroutineScope): Job =
        scope.launch { events.collect { add(it) } }
}

private fun formatLogTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
