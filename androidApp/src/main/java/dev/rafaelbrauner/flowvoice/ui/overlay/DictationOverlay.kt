package dev.rafaelbrauner.flowvoice.ui.overlay

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
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
import kotlinx.coroutines.flow.StateFlow

private const val CLOCK_REFRESH_MS = 200L
private const val RESULT_VISIBLE_MS = 4_000L
private const val BUBBLE_LABEL_IDLE = "Ditar"
private const val BUBBLE_LABEL_STOP = "Encerrar ditado"
private const val BUBBLE_LABEL_BUSY = "Ditado em andamento"
private const val MOVE_UP = "Mover para cima"
private const val MOVE_DOWN = "Mover para baixo"
private const val MOVE_OTHER_SIDE = "Mover para o outro lado"

@Composable
fun DictationOverlay(
    pipeline: DictationPipeline,
    host: BubbleHost,
    bubbleHidden: StateFlow<Boolean>,
    onModeChange: (OverlayMode) -> Unit,
    onSessionOwned: (OverlayOwnership) -> Unit
) {
    val session by pipeline.session.collectAsState()
    val status = session.status
    val target by pipeline.target.collectAsState()
    val segments by pipeline.segments.collectAsState()
    val direct by pipeline.directInsertion.collectAsState()
    var ownership by remember { mutableStateOf(OverlayOwnership.released()) }
    val owned = ownership.owned
    var dismissed by remember { mutableStateOf<DictationPipelineSession?>(null) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val latestModeChange by rememberUpdatedState(onModeChange)
    val latestSessionOwned by rememberUpdatedState(onSessionOwned)
    val startRequest by OverlayStartRequests.count.collectAsState()
    // "Ocultar botão" só tira a bolha: a barra e a prévia do ditado em andamento continuam na tela (P159).
    val hiddenBubble by bubbleHidden.collectAsState()

    val beginSession: (Boolean) -> Unit = { startedHere ->
        ownership = OverlayOwnership.begin(pipeline.session.value, startedHere)
        dismissed = null
        elapsedMs = 0L
        latestSessionOwned(ownership)
        pipeline.requestStart(DictationTarget.ActiveField)
    }

    LaunchedEffect(startRequest) {
        if (OverlayStartRequests.takePending() && !pipeline.status.value.isBusy) {
            // Pedido do app (Início): a bolha executa o ditado, mas não é dona dele (P40).
            beginSession(false)
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

    // O pipeline marca a sessão direta antes de publicar o status: lido direto, sem esperar o próximo
    // quadro, a barra de revisão não pisca no começo da sessão direta.
    val directNow = remember(session, direct) { pipeline.directInsertion.value }
    val isDirect = directNow.active && directNow.sessionId == session.id
    val live = remember(segments) { pipeline.liveText() }
    val proofreading = remember(status) { pipeline.proofreadingEnabled }
    val barState = if (isDirect) {
        DictationBarState.Hidden
    } else {
        DictationBarModel.from(
            status = status,
            target = target,
            live = live,
            elapsedMs = elapsedMs,
            proofreadingEnabled = proofreading,
            owned = owned,
            dismissed = session == dismissed
        )
    }
    val previewState = if (isDirect) {
        DirectPreviewModel.from(
            status = status,
            progress = directNow,
            transcribing = segments.any { it.status == TranscriptionSegment.Status.Transcribing },
            elapsedMs = elapsedMs,
            owned = owned,
            dismissed = session == dismissed
        )
    } else {
        DirectPreviewState.Hidden
    }
    val mode = when {
        previewState != DirectPreviewState.Hidden -> OverlayMode.Preview
        barState != DictationBarState.Hidden -> OverlayMode.Bar
        else -> OverlayMode.Bubble
    }
    LaunchedEffect(mode) { latestModeChange(mode) }

    // A prévia mora numa janela própria, posicionada pelo serviço longe do cursor (P139).
    val previewActions = remember(pipeline) {
        previewActionsFor(pipeline) {
            dismissed = session
            ownership = OverlayOwnership.released()
        }
    }
    SideEffect {
        host.updatePreview(if (mode == OverlayMode.Preview) previewState else DirectPreviewState.Hidden, previewActions)
    }

    val onBubbleTap: () -> Unit = {
        val current = pipeline.session.value
        val currentDirect = pipeline.directInsertion.value.let { it.active && it.sessionId == current.id }
        when {
            currentDirect && ownership.owned && current.status == DictationPipelineStatus.Recording ->
                pipeline.requestFinalize()
            currentDirect && ownership.owned && current.status.isBusy -> Unit
            current.status.isBusy -> {
                // Adotar dá os controles na barra; cancelar ao ocultar continua sendo só de quem
                // começou o ditado (P40).
                dismissed = null
                ownership = OverlayOwnership.adopt(current.status)
            }
            else -> beginSession(true)
        }
    }
    val bubbleLabel = when {
        isDirect && owned && status == DictationPipelineStatus.Recording -> BUBBLE_LABEL_STOP
        status.isBusy -> BUBBLE_LABEL_BUSY
        else -> BUBBLE_LABEL_IDLE
    }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    FlowVoiceTheme {
        if (mode == OverlayMode.Bar) {
            when (val state = barState) {
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
                DictationBarState.Hidden -> Unit
            }
        } else if (!hiddenBubble) {
            DictationBubble(
                label = bubbleLabel,
                pulsing = status.isBusy,
                touchSlopPx = touchSlop,
                onTap = onBubbleTap,
                onDrag = host::onDrag,
                onDragEnd = host::onDragEnd,
                onMove = host::onMove
            )
        }
    }
}

// Conteúdo da janela da prévia: largura dada pela janela e altura limitada ao espaço livre longe do cursor.
@Composable
fun PreviewCardOverlay(card: StateFlow<PreviewCardUi>) {
    val ui by card.collectAsState()
    val maxHeight = with(LocalDensity.current) { ui.maxHeightPx.toDp() }
    val actions = ui.actions
    FlowVoiceTheme {
        DirectPreviewCard(
            state = ui.state,
            compact = ui.compact,
            onCancel = { actions?.onCancel?.invoke() },
            onInsertHere = { actions?.onInsertHere?.invoke() },
            onDismiss = { actions?.onDismiss?.invoke() },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (ui.maxHeightPx > 0) Modifier.heightIn(max = maxHeight) else Modifier)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DictationBubble(
    label: String,
    pulsing: Boolean,
    touchSlopPx: Float,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onMove: (BubbleMove) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val gesture = remember(touchSlopPx) { BubbleGesture(touchSlopPx) }
    val latestTap by rememberUpdatedState(onTap)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val latestMove by rememberUpdatedState(onMove)
    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick(label = label) {
                    latestTap()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(MOVE_UP) { latestMove(BubbleMove.Up); true },
                    CustomAccessibilityAction(MOVE_DOWN) { latestMove(BubbleMove.Down); true },
                    CustomAccessibilityAction(MOVE_OTHER_SIDE) { latestMove(BubbleMove.OtherSide); true }
                )
            }
            // Coordenadas brutas: a janela anda junto com o dedo (P138).
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> gesture.down(event.rawX, event.rawY)
                    MotionEvent.ACTION_MOVE -> gesture.move(event.rawX, event.rawY)?.let { latestDrag(it.dx, it.dy) }
                    MotionEvent.ACTION_UP -> when (gesture.up()) {
                        BubbleGesture.End.Tap -> latestTap()
                        BubbleGesture.End.DragEnd -> latestDragEnd()
                        BubbleGesture.End.None -> Unit
                    }
                    MotionEvent.ACTION_CANCEL -> if (gesture.cancel() == BubbleGesture.End.DragEnd) latestDragEnd()
                }
                true
            }
            // A janela é retangular: sombra e anel pulsante que passam da folga eram cortados nas bordas dela e
            // formavam um halo quadrado. O recorte em círculo mantém tudo redondo, e a sombra menor esmaece antes.
            .clip(CircleShape)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .shadow(4.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.accentBorder, CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            MicButton(
                onClick = null,
                size = 40.dp,
                iconSize = 20.dp,
                pulsing = pulsing,
                contentDescription = null
            )
        }
    }
}

@Composable
fun DirectPreviewCard(
    state: DirectPreviewState,
    onCancel: () -> Unit,
    onInsertHere: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = FlowVoiceTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .padding(6.dp)
            .shadow(8.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(colors.surface, shape)
            .border(1.dp, colors.accentBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (state) {
            is DirectPreviewState.Live -> DirectPreviewLive(state, compact, onCancel, onInsertHere)
            is DirectPreviewState.Result -> DirectPreviewResult(state, onDismiss)
            DirectPreviewState.Hidden -> Unit
        }
    }
}

// Estado e botões ficam sempre; com pouco espaço (compact), os textos perdem linhas e rolam.
@Composable
private fun ColumnScope.DirectPreviewLive(
    state: DirectPreviewState.Live,
    compact: Boolean,
    onCancel: () -> Unit,
    onInsertHere: () -> Unit
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val recording = state.phase == DirectPhase.Recording && state.notice == null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (recording) colors.accent else colors.textSecondary, CircleShape)
        )
        Text(text = state.clock, style = typography.monoValue, color = colors.textSecondary, maxLines = 1)
        MonoLabel(
            text = state.status,
            modifier = Modifier.weight(1f),
            color = if (state.notice != null) colors.accentText else colors.textSecondary,
            style = typography.monoRoute,
            maxLines = if (compact) 1 else 2
        )
    }
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        state.notice?.let { notice ->
            Text(
                text = notice,
                style = typography.bodySmall,
                color = colors.accentText,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        state.warning?.takeIf { it != state.notice }?.let { warning ->
            Text(
                text = warning,
                style = typography.bodySmall,
                color = colors.accentText,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (state.typedTail.isNotEmpty()) {
            LabeledText(
                label = DirectPreviewModel.TYPED_LABEL,
                text = state.typedTail,
                color = colors.textPrimary,
                maxLines = if (compact) 1 else 2
            )
        }
        if (state.transcribing) {
            ProvisionalText(
                finalized = "",
                provisional = DirectPreviewModel.TRANSCRIBING,
                style = typography.bodySmall,
                provisionalColor = colors.textSecondary,
                maxLines = 1
            )
        }
        if (state.pending.isNotEmpty()) {
            LabeledText(
                label = DirectPreviewModel.PENDING_LABEL,
                text = state.pending,
                color = colors.textSecondary,
                maxLines = if (compact) 1 else 3
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(
            text = state.cancelLabel,
            onClick = onCancel,
            variant = PillButtonVariant.Outline,
            height = 40.dp,
            horizontalPadding = 14.dp
        )
        Spacer(Modifier.weight(1f))
        if (state.canInsertHere) {
            PillButton(
                text = DirectPreviewModel.INSERT_HERE,
                onClick = onInsertHere,
                variant = PillButtonVariant.Primary,
                height = 40.dp
            )
        }
    }
}

@Composable
private fun LabeledText(label: String, text: String, color: Color, maxLines: Int) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        MonoLabel(
            text = label,
            modifier = Modifier.padding(top = 4.dp),
            color = colors.textSecondary,
            style = typography.monoLabelSmall
        )
        Text(
            text = text,
            style = typography.body.copy(fontSize = 14.sp, lineHeight = 19.6.sp),
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DirectPreviewResult(state: DirectPreviewState.Result, onDismiss: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onDismiss
            )
            .heightIn(min = 44.dp),
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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        DictationResult(
            state = DictationBarState.Result(success = true, message = "Inserido no campo ativo · 1,1 s"),
            onDismiss = {}
        )
        DictationBubble(
            label = BUBBLE_LABEL_IDLE,
            pulsing = false,
            touchSlopPx = 8f,
            onTap = {},
            onDrag = { _, _ -> },
            onDragEnd = {},
            onMove = {}
        )
    }
}

@Preview
@Composable
private fun DirectPreviewCardPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        DirectPreviewCard(
            state = DirectPreviewState.Live(
                phase = DirectPhase.Recording,
                clock = "0:12",
                status = DirectPreviewModel.STATUS_LISTENING,
                typedTail = "Bom dia, Marina. Consegui fechar o orçamento",
                pending = "",
                transcribing = true,
                notice = null,
                warning = null,
                canInsertHere = false,
                cancelLabel = DirectPreviewModel.CANCEL
            ),
            onCancel = {},
            onInsertHere = {},
            onDismiss = {}
        )
        DirectPreviewCard(
            state = DirectPreviewState.Live(
                phase = DirectPhase.Recording,
                clock = "0:18",
                status = DirectPreviewModel.STATUS_PAUSED,
                typedTail = "Bom dia, Marina. Consegui fechar o orçamento",
                pending = "do projeto ontem à noite",
                transcribing = false,
                notice = "o foco mudou de app; toque em Inserir aqui para escrever no app atual",
                warning = null,
                canInsertHere = true,
                cancelLabel = DirectPreviewModel.CANCEL
            ),
            onCancel = {},
            onInsertHere = {},
            onDismiss = {},
            compact = true
        )
        DirectPreviewCard(
            state = DirectPreviewState.Result(success = true, message = "Digitado no campo · 11 palavras"),
            onCancel = {},
            onInsertHere = {},
            onDismiss = {}
        )
    }
}
