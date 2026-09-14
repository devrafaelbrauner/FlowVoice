package dev.rafaelbrauner.flowvoice.shared.benchmark

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow

data class BenchmarkClip(
    val id: String,
    val reference: String,
    val window: DictationWindow
)
