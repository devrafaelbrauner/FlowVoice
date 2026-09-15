package dev.rafaelbrauner.flowvoice.logging

// O texto ditado só vai ao logcat para diagnóstico explícito (P135): build de depuração e um
// marcador criado por adb há menos de 1 h, que expira sozinho.
//   adb shell run-as dev.rafaelbrauner.flowvoice touch files/transcript-text-logging
object TranscriptTextLogging {
    const val MARKER_FILE = "transcript-text-logging"
    private const val VALIDITY_MS = 60 * 60 * 1_000L

    fun isEnabled(debuggable: Boolean, markerModifiedAtMs: Long?, nowMs: Long): Boolean =
        debuggable && markerModifiedAtMs != null && nowMs - markerModifiedAtMs in 0 until VALIDITY_MS
}
