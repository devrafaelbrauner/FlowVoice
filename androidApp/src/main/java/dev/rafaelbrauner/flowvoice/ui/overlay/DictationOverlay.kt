package dev.rafaelbrauner.flowvoice.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import dev.rafaelbrauner.flowvoice.ui.components.FvPreviewSurface
import dev.rafaelbrauner.flowvoice.ui.components.MicButton
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.components.PillButtonVariant
import dev.rafaelbrauner.flowvoice.ui.components.ProvisionalText
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.components.Waveform
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import kotlinx.coroutines.delay

private const val CLOCK_REFRESH_MS = 200L
private const val RESULT_VISIBLE_MS = 4_000L

@Composable
fun DictationOverlay(
    pipeline: DictationPipeline,
    onModeChange: (OverlayMode) -> Unit,
    onSessionOwned: () -> Unit
) {
    val session by pipeline.session.collectAsState()
    val status = session.status
    val target by pipeline.target.collectAsState()
    val segments by pipeline.segments.collectAsState()
    var ownership by remember { mutableStateOf(OverlayOwnership.released()) }
    val owned = ownership.owned
    var dismissed by remember { mutableStateOf<DictationPipelineSession?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val latestModeChange by rememberUpdatedState(onModeChange)
    val latestSessionOwned by rememberUpdatedState(onSessionOwned)
    val startRequest by OverlayStartRequests.count.collectAsState()

    val beginSession = {
        ownership = OverlayOwnership.begin(pipeline.session.value)
        dismissed = null
        elapsedMs = 0L
        latestSessionOwned()
        pipeline.requestStart(DictationTarget.ActiveField)
    }

    LaunchedEffect(startRequest) {
        if (OverlayStartRequests.takePending() && !pipeline.status.value.isBusy) {
            beginSession()
        }
    }

    LaunchedEffect(session) {
        val current = pipeline.session.value
        ownership = OverlayOwnership.onSession(ownership, current)
        when (current.status) {
            DictationPipelineStatus.Starting,
            DictationPipelineStatus.Recording -> while (true) {
                elapsedMs = pipeline.capturedDurationMs
                delay(CLOCK_REFRESH_MS)
            }
            is DictationPipelineStatus.Completed,
            is DictationPipelineStatus.Failed -> if (ownership.owned) {
                delay(RESULT_VISIBLE_MS)
                dismissed = current
                ownership = OverlayOwnership.released()
            }
            else -> Unit
        }
    }

    val live = remember(segments) { pipeline.liveText() }
    val proofreading = remember(status) { pipeline.proofreadingEnabled }
    val state = DictationBarModel.from(
        status = status,
        target = target,
        live = live,
        elapsedMs = elapsedMs,
        proofreadingEnabled = proofreading,
        owned = owned,
        dismissed = session == dismissed
    )
    val mode = if (state is DictationBarState.Hidden) OverlayMode.Bubble else OverlayMode.Bar
    LaunchedEffect(mode) { latestModeChange(mode) }

    FlowVoiceTheme {
        when (state) {
            DictationBarState.Hidden -> DictationBubble(
                busy = status.isBusy,
                onTap = {
                    val current = pipeline.status.value
                    if (current.isBusy) {
                        dismissed = null
                        ownership = OverlayOwnership.adopt(current)
                        latestSessionOwned()
                    } else {
                        beginSession()
                    }
                }
            )
            is DictationBarState.Live -> DictationBar(
                state = state,
                onCancel = { pipeline.requestCancel() },
                onInsert = {
                    when (pipeline.status.value) {
                        DictationPipelineStatus.Recording -> pipeline.requestFinalizeForReview()
                        is DictationPipelineStatus.Ready -> pipeline.requestInsertReady()
                        else -> Unit
                    }
                },
                onStop = { pipeline.requestFinalize() }
            )
            is DictationBarState.Result -> DictationResult(
                state = state,
                onDismiss = {
                    dismissed = session
                    ownership = OverlayOwnership.released()
                }
            )
        }
    }
}

@Composable
fun DictationBubble(busy: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FlowVoiceTheme.colors
    Box(modifier = modifier.padding(10.dp)) {
        Box(
            modifier = Modifier
                .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.accentBorder, CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            MicButton(
                onClick = onTap,
                size = 40.dp,
                iconSize = 20.dp,
                pulsing = busy,
                contentDescription = if (busy) "Ditado em andamento" else "Ditar"
            )
        }
    }
}

@Composable
fun DictationBar(
    state: DictationBarState.Live,
    onCancel: () -> Unit,
    onInsert: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val topBorder = colors.accentBorderSoft
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                drawLine(topBorder, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Waveform(animating = state.phase == BarPhase.Recording)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 20.dp, max = 58.dp)
                    .clipToBounds()
            ) {
                ProvisionalText(
                    finalized = state.finalized,
                    provisional = state.provisional,
                    style = typography.body.copy(fontSize = 14.sp, lineHeight = 19.6.sp),
                    maxLines = 3,
                    underlineOffset = 3.dp
                )
            }
            Text(
                text = state.clock,
                style = typography.monoValue,
                color = colors.iconMuted,
                maxLines = 1
            )
        }
        state.warning?.let { warning ->
            Text(
                text = warning,
                style = typography.bodySmall,
                color = colors.accentText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillButton(
                text = "Cancelar",
                onClick = onCancel,
                variant = PillButtonVariant.Outline,
                height = 34.dp,
                horizontalPadding = 14.dp
            )
            MonoLabel(
                text = state.route,
                modifier = Modifier.weight(1f),
                color = colors.textTertiary,
                style = typography.monoRoute,
                maxLines = 2
            )
            if (state.destination == DictationTarget.Note) {
                PillButton(
                    text = "Parar",
                    onClick = onStop,
                    variant = PillButtonVariant.Primary,
                    height = 34.dp,
                    enabled = state.canStop
                )
            } else {
                PillButton(
                    text = "Inserir",
                    onClick = onInsert,
                    variant = PillButtonVariant.Primary,
                    height = 34.dp,
                    enabled = state.canInsert
                )
            }
        }
    }
}

@Composable
fun DictationResult(state: DictationBarState.Result, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val topBorder = colors.hairline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                drawLine(topBorder, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onDismiss
            )
            .heightIn(min = 44.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (state.success) colors.accent else colors.destructive, RoundedCornerShape(2.dp))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.message,
                style = typography.buttonSecondary,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            state.detail?.let { detail ->
                Text(
                    text = detail,
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview
@Composable
private fun DictationBarPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        DictationBar(
            state = DictationBarState.Live(
                phase = BarPhase.Recording,
                finalized = "Bom dia, Marina. Consegui fechar o orçamento",
                provisional = "do projeto ontem à noite",
                clock = "0:06",
                route = DictationBarModel.ROUTE_LISTENING,
                canInsert = true
            ),
            onCancel = {},
            onInsert = {},
            onStop = {}
        )
        DictationBar(
            state = DictationBarState.Live(
                phase = BarPhase.Ready,
                finalized = "Bom dia, Marina. Consegui fechar o orçamento do projeto ontem à noite.",
                provisional = "",
                clock = "0:09",
                route = DictationBarModel.ROUTE_READY,
                canInsert = true
            ),
            onCancel = {},
            onInsert = {},
            onStop = {}
        )
        DictationResult(
            state = DictationBarState.Result(success = true, message = "Inserido no campo ativo · 1,1 s"),
            onDismiss = {}
        )
        DictationBubble(busy = false, onTap = {})
    }
}
