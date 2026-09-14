package dev.rafaelbrauner.flowvoice.ui.screens.diagnostics

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow

internal fun mergeWindows(windows: List<DictationWindow>): DictationWindow {
    require(windows.isNotEmpty()) { "windows must not be empty" }
    val first = windows.first()
    val pcm = ByteArray(windows.sumOf { it.pcm.size })
    var offset = 0
    windows.forEach { window ->
        window.pcm.copyInto(pcm, offset)
        offset += window.pcm.size
    }
    return DictationWindow(
        index = 0,
        pcm = pcm,
        format = first.format,
        startedAtMs = first.startedAtMs,
        finishedAtMs = windows.last().finishedAtMs
    )
}
