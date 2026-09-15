package dev.rafaelbrauner.flowvoice.ui.screens.home

import dev.rafaelbrauner.flowvoice.shared.overlay.OverlayStartGuard
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus

enum class MicAction { OpenOnboarding, OverlayUnsupported, RequestOverlayPermission, StartDictation }

fun micAction(
    accessibilityRunning: Boolean,
    microphoneGranted: Boolean,
    sdkInt: Int,
    canDrawOverlays: Boolean
): MicAction = when {
    !accessibilityRunning || !microphoneGranted -> MicAction.OpenOnboarding
    sdkInt < OverlayStartGuard.MIN_SDK_APPLICATION_OVERLAY -> MicAction.OverlayUnsupported
    !canDrawOverlays -> MicAction.RequestOverlayPermission
    else -> MicAction.StartDictation
}

fun noteToResumeOnMic(pipelineBusy: Boolean, activeNoteId: String?): String? =
    activeNoteId.takeIf { pipelineBusy }

fun startOutcome(previous: DictationPipelineSession, session: DictationPipelineSession): DictationPipelineStatus? =
    session.status.takeIf {
        session.id > previous.id && (it == DictationPipelineStatus.Recording || it is DictationPipelineStatus.Failed)
    }

// Depois do aviso "O microfone não respondeu a tempo.", a sessão pedida por aquele toque (a
// seguinte à anterior) não pode seguir gravando sem controle: se ainda estiver ocupada, é cancelada (P133).
fun cancelAfterStartTimeout(previous: DictationPipelineSession, session: DictationPipelineSession): Boolean =
    session.id == previous.id + 1 && session.status.isBusy

fun noteSnippet(title: String, body: String): String {
    val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val first = lines.firstOrNull() ?: return ""
    return if (first == title.trim()) lines.getOrElse(1) { "" } else first
}
