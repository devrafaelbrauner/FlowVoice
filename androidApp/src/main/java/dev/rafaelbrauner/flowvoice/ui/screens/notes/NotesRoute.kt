package dev.rafaelbrauner.flowvoice.ui.screens.notes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.notes.NoteDictationCoordinator
import dev.rafaelbrauner.flowvoice.shared.notes.NoteStore
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.components.ProvisionalText
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.components.Waveform
import dev.rafaelbrauner.flowvoice.ui.components.fieldDescription
import dev.rafaelbrauner.flowvoice.ui.icons.FlowVoiceIcons
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun NotesRoute(initialNoteId: String?, modifier: Modifier = Modifier) {
    val store = rememberKoin<NoteStore>()
    val pipeline = rememberKoin<DictationPipeline>()
    val coordinator = rememberKoin<NoteDictationCoordinator>()
    val state = remember(store, coordinator) { NotesScreenState(store, initialNoteId, coordinator) }
    val context = LocalContext.current
    val status by pipeline.status.collectAsState()
    val segments by pipeline.segments.collectAsState()
    val dictationState by coordinator.state.collectAsState()
    var pendingStartNoteId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialNoteId) {
        state.refresh()
        (initialNoteId ?: coordinator.activeNoteId)?.let(state::select)
    }
    LaunchedEffect(dictationState, status) { state.refresh() }

    val startDictation: (String) -> Unit = { noteId ->
        val current = pipeline.status.value
        if (current.isBusy) {
            state.showError("Já há um ditado em andamento.")
        } else {
            state.beginDictation(noteId, current)
            pipeline.requestStart(DictationTarget.Note)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val noteId = pendingStartNoteId
        pendingStartNoteId = null
        when {
            !granted -> state.showError("Permissão de microfone negada.")
            noteId != null -> startDictation(noteId)
        }
    }
    val requestDictation: (String) -> Unit = { noteId ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startDictation(noteId)
        } else {
            pendingStartNoteId = noteId
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val dictatingHere = dictationState.noteId != null && status.isBusy
    val provisional = if (dictatingHere) {
        LivePreviewAssembler.assemble(segments, sessionComplete = false).full
    } else {
        ""
    }

    NotesScreen(
        notes = state.notes,
        selected = state.selected,
        panelOpen = state.panelOpen,
        nowMs = System.currentTimeMillis(),
        dictation = when {
            !dictatingHere -> NoteDictationBar.Idle
            status == DictationPipelineStatus.Transcribing -> NoteDictationBar.Transcribing
            else -> NoteDictationBar.Listening
        },
        dictationNoteId = dictationState.noteId,
        provisional = provisional,
        message = state.message,
        pendingDeleteId = state.pendingDeleteId,
        onTogglePanel = state::togglePanel,
        onSelect = state::select,
        onNewNote = { requestDictation(state.createNote().id) },
        onTitleChange = state::updateTitle,
        onBodyChange = state::updateBody,
        onDeleteTap = { id ->
            val wasDictating = dictationState.noteId == id
            if (state.onDeleteTap(id) && wasDictating) pipeline.requestCancel()
        },
        onDictate = requestDictation,
        onStop = { pipeline.requestFinalize() },
        onDismissMessage = state::dismissMessage,
        modifier = modifier
    )
}

enum class NoteDictationBar { Idle, Listening, Transcribing }

@Composable
internal fun NotesScreen(
    notes: List<Note>,
    selected: Note?,
    panelOpen: Boolean,
    nowMs: Long,
    dictation: NoteDictationBar,
    dictationNoteId: String?,
    provisional: String,
    message: NotesMessage?,
    pendingDeleteId: String?,
    onTogglePanel: () -> Unit,
    onSelect: (String) -> Unit,
    onNewNote: () -> Unit,
    onTitleChange: (String, String) -> Unit,
    onBodyChange: (String, String) -> Unit,
    onDeleteTap: (String) -> Unit,
    onDictate: (String) -> Unit,
    onStop: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (panelOpen) {
            NotesPanel(
                notes = notes,
                selectedId = selected?.id,
                nowMs = nowMs,
                onCollapse = onTogglePanel,
                onSelect = onSelect,
                onNewNote = onNewNote
            )
        } else {
            CollapsedNotesPanel(count = notes.size, onExpand = onTogglePanel)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            if (selected == null) {
                EmptyNoteDetail(onNewNote = onNewNote)
            } else {
                NoteDetail(
                    note = selected,
                    nowMs = nowMs,
                    dictation = if (dictationNoteId == selected.id) dictation else NoteDictationBar.Idle,
                    provisional = if (dictationNoteId == selected.id) provisional else "",
                    message = message,
                    deletePending = pendingDeleteId == selected.id,
                    onTitleChange = { onTitleChange(selected.id, it) },
                    onBodyChange = { onBodyChange(selected.id, it) },
                    onDeleteTap = { onDeleteTap(selected.id) },
                    onDictate = { onDictate(selected.id) },
                    onStop = onStop,
                    onDismissMessage = onDismissMessage
                )
            }
        }
    }
}

@Composable
private fun NotesPanel(
    notes: List<Note>,
    selectedId: String?,
    nowMs: Long,
    onCollapse: () -> Unit,
    onSelect: (String) -> Unit,
    onNewNote: () -> Unit
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = Modifier
            .width(176.dp)
            .fillMaxHeight()
            .background(colors.chrome)
            .rightHairline(colors.hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 0.dp, top = 4.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "NOTAS · ${notes.size}",
                style = typography.monoLabelSmall.copy(letterSpacing = 0.16.em),
                color = colors.textTertiary,
                maxLines = 1,
                softWrap = false
            )
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClickLabel = "Recolher lista de notas", onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(FlowVoiceRadius.chip)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FlowVoiceIcons.ChevronDoubleLeft,
                        contentDescription = "Recolher lista de notas",
                        tint = colors.iconMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            notes.forEach { note ->
                NoteListItem(
                    note = note,
                    selected = note.id == selectedId,
                    nowMs = nowMs,
                    onClick = { onSelect(note.id) }
                )
            }
        }
        Box(modifier = Modifier.padding(10.dp)) {
            PillButton(
                text = "Nova nota",
                onClick = onNewNote,
                icon = FlowVoiceIcons.MicFilled,
                height = 40.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NoteListItem(note: Note, selected: Boolean, nowMs: Long, onClick: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val shape = RoundedCornerShape(FlowVoiceRadius.field)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accentSurface else colors.surface)
            .border(1.dp, if (selected) colors.accentBorder else colors.hairlineCard, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Text(
            text = note.title.ifBlank { NotesScreenState.UNTITLED },
            style = typography.itemTitleSmall,
            color = if (selected) colors.accentText else colors.keyValue,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val snippet = NoteBodies.snippet(note.body)
        if (snippet.isNotEmpty()) {
            Text(
                text = snippet,
                style = typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            text = NoteTimeFormat.label(note.updatedAtMs, nowMs),
            style = typography.monoTimestamp.copy(fontSize = 10.sp),
            color = colors.textTimestamp,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun CollapsedNotesPanel(count: Int, onExpand: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = Modifier
            .width(42.dp)
            .fillMaxHeight()
            .background(colors.chrome)
            .rightHairline(colors.hairline)
            .clickable(role = Role.Button, onClickLabel = "Expandir lista de notas", onClick = onExpand)
            .semantics { contentDescription = "Expandir lista de notas, $count notas" }
            .padding(top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = FlowVoiceIcons.ChevronDoubleRight,
            contentDescription = null,
            tint = colors.iconMuted,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "NOTAS",
            style = typography.monoLabelSmall.copy(letterSpacing = 0.18.em),
            color = colors.textTertiary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.verticalRl()
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = typography.monoTimestamp.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = colors.onAccent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NoteDetail(
    note: Note,
    nowMs: Long,
    dictation: NoteDictationBar,
    provisional: String,
    message: NotesMessage?,
    deletePending: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onDeleteTap: () -> Unit,
    onDictate: () -> Unit,
    onStop: () -> Unit,
    onDismissMessage: () -> Unit
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val dictating = dictation != NoteDictationBar.Idle
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${NoteTimeFormat.label(note.updatedAtMs, nowMs)} · salvo",
                style = typography.monoStatus,
                color = colors.textTertiary,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(
                        enabled = !dictating,
                        role = Role.Button,
                        onClickLabel = if (deletePending) "Confirmar exclusão da nota" else "Apagar nota",
                        onClick = onDeleteTap
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (deletePending) "confirmar?" else "apagar",
                    style = typography.monoStatus,
                    color = if (deletePending) colors.destructiveText else colors.textTertiary,
                    maxLines = 1,
                    modifier = Modifier.alpha(if (dictating) 0.4f else 1f)
                )
            }
        }
        BasicTextField(
            value = note.title,
            onValueChange = onTitleChange,
            enabled = !dictating,
            textStyle = typography.noteTitle.copy(color = colors.textPrimary, letterSpacing = (-0.02).em),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 2.dp)
                .fieldDescription("Título da nota", note.title)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 12.dp)
        ) {
            if (dictating) {
                ProvisionalText(
                    finalized = note.body,
                    provisional = provisional,
                    style = typography.body,
                    finalizedColor = colors.noteBody,
                    provisionalColor = colors.provisional,
                    underlineOffset = 4.dp
                )
            } else {
                Box {
                    if (note.body.isEmpty()) {
                        Text(
                            text = "Escreva ou dite…",
                            style = typography.body,
                            color = colors.textTertiary
                        )
                    }
                    BasicTextField(
                        value = note.body,
                        onValueChange = onBodyChange,
                        textStyle = typography.body.copy(color = colors.noteBody),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fieldDescription("Corpo da nota", note.body)
                    )
                }
            }
        }
        if (message != null) {
            NoteMessageRow(message = message, onDismiss = onDismissMessage)
        }
        NoteDictationBarView(
            state = dictation,
            onDictate = onDictate,
            onStop = onStop
        )
    }
}

@Composable
private fun NoteMessageRow(message: NotesMessage, onDismiss: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message.text,
            style = typography.bodySmall,
            color = when (message) {
                is NotesMessage.Error -> colors.destructiveText
                is NotesMessage.Warning -> colors.accentText
            },
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClickLabel = "Dispensar aviso", onClick = onDismiss)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "ok", style = typography.monoStatus, color = colors.textTertiary)
        }
    }
}

@Composable
private fun NoteDictationBarView(
    state: NoteDictationBar,
    onDictate: () -> Unit,
    onStop: () -> Unit
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val shape = RoundedCornerShape(FlowVoiceRadius.card)
    val idle = state == NoteDictationBar.Idle
    val barClick = if (idle) {
        Modifier.clickable(role = Role.Button, onClickLabel = "Ditar nesta nota", onClick = onDictate)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .then(barClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Waveform(
            bars = 4,
            height = 20.dp,
            color = if (idle) colors.textTertiary else colors.accent,
            animating = state == NoteDictationBar.Listening,
            delayStepMs = 120
        )
        Text(
            text = when (state) {
                NoteDictationBar.Idle -> "Ditar nesta nota"
                NoteDictationBar.Listening -> "Ouvindo… fale para continuar"
                NoteDictationBar.Transcribing -> "Transcrevendo…"
            },
            style = typography.bodyMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, lineHeight = 1.3.em),
            color = if (idle) colors.textSecondary else colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (idle) {
            RoundBarButton(
                label = "Ditar nesta nota",
                background = colors.accent,
                onClick = onDictate
            ) {
                Icon(
                    imageVector = FlowVoiceIcons.MicFilled,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            RoundBarButton(
                label = "Parar ditado",
                background = colors.destructive,
                enabled = state == NoteDictationBar.Listening,
                onClick = onStop
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.surface)
                )
            }
        }
    }
}

@Composable
private fun RoundBarButton(
    label: String,
    background: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun EmptyNoteDetail(onNewNote: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "NENHUMA NOTA",
            style = typography.monoLabel,
            color = colors.textTertiary
        )
        Text(
            text = "Toque em Nova nota para ditar a primeira.",
            style = typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(16.dp))
        PillButton(text = "Nova nota", onClick = onNewNote, icon = FlowVoiceIcons.MicFilled, height = 40.dp)
    }
}

private fun Modifier.rightHairline(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val x = size.width - stroke / 2f
    drawLine(color = color, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = stroke)
}

private fun Modifier.verticalRl(): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2
        ) {
            rotationZ = 90f
        }
    }
}

private fun previewNotes(nowMs: Long): List<Note> = listOf(
    Note("1", "Reunião de orçamento", "Fechamos o escopo da fase 2 com a Marina. O orçamento sobe 12% por causa da integração com o ERP, e o prazo continua em março.", nowMs - 3_600_000L),
    Note("2", "Ideias para o F05", "Rodar o corpus pt-BR com três modelos antes de escolher o padrão.", nowMs - 90_000_000L),
    Note("3", "Lista do mercado", "café, azeite, pilha AA, ração da Nina", nowMs - 95_000_000L),
    Note("4", "Ligar para o contador", "Confirmar a nota do trimestre.", nowMs - 400_000_000L)
)

@Preview(widthDp = 412, heightDp = 780)
@Composable
private fun NotesScreenPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        val now = 1_789_335_000_000L
        val notes = previewNotes(now)
        NotesScreen(
            notes = notes,
            selected = notes.first(),
            panelOpen = true,
            nowMs = now,
            dictation = NoteDictationBar.Listening,
            dictationNoteId = "1",
            provisional = "precisa confirmar com o financeiro na sexta",
            message = null,
            pendingDeleteId = null,
            onTogglePanel = {},
            onSelect = {},
            onNewNote = {},
            onTitleChange = { _, _ -> },
            onBodyChange = { _, _ -> },
            onDeleteTap = {},
            onDictate = {},
            onStop = {},
            onDismissMessage = {}
        )
    }
}

@Preview(widthDp = 412, heightDp = 780)
@Composable
private fun NotesScreenCollapsedPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        val now = 1_789_335_000_000L
        val notes = previewNotes(now)
        NotesScreen(
            notes = notes,
            selected = notes.first(),
            panelOpen = false,
            nowMs = now,
            dictation = NoteDictationBar.Idle,
            dictationNoteId = null,
            provisional = "",
            message = NotesMessage.Warning("Trecho 2 de 3 falhou (timeout): texto incompleto"),
            pendingDeleteId = null,
            onTogglePanel = {},
            onSelect = {},
            onNewNote = {},
            onTitleChange = { _, _ -> },
            onBodyChange = { _, _ -> },
            onDeleteTap = {},
            onDictate = {},
            onStop = {},
            onDismissMessage = {}
        )
    }
}
