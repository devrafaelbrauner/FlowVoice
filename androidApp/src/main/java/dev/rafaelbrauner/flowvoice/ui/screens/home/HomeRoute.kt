package dev.rafaelbrauner.flowvoice.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import dev.rafaelbrauner.flowvoice.service.FlowVoiceOverlayService
import dev.rafaelbrauner.flowvoice.shared.notes.NoteDictationCoordinator
import dev.rafaelbrauner.flowvoice.shared.notes.NoteStore
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.ui.components.FvCard
import dev.rafaelbrauner.flowvoice.ui.components.MicButton
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.StatCard
import dev.rafaelbrauner.flowvoice.ui.components.StatusPill
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.components.Wordmark
import dev.rafaelbrauner.flowvoice.ui.overlay.OverlayStartRequests
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.shell.findActivity
import dev.rafaelbrauner.flowvoice.ui.shell.startActivitySafely
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val RECENT_NOTES = 2
private const val START_TIMEOUT_MS = 4_000L
private const val LATE_START_GRACE_MS = 10_000L
private const val NO_DATA = "—"

@Immutable
data class RecentNote(
    val id: String,
    val title: String,
    val snippet: String,
    val time: String
)

@Composable
fun HomeRoute(
    onOpenNotes: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val noteStore = rememberKoin<NoteStore>()
    val pipeline = rememberKoin<DictationPipeline>()
    val scope = rememberCoroutineScope()
    var accessibilityActive by remember { mutableStateOf(FlowVoiceAccessibilityService.isRunning) }
    var notes by remember { mutableStateOf(noteStore.list().take(RECENT_NOTES)) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var askedNotifications by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        accessibilityActive = FlowVoiceAccessibilityService.isRunning
        notes = noteStore.list().take(RECENT_NOTES)
        nowMs = System.currentTimeMillis()
        onPauseOrDispose { }
    }

    val coordinator = rememberKoin<NoteDictationCoordinator>()
    val latestOpenNote by rememberUpdatedState(onOpenNote)
    val starter = remember(context, pipeline, scope, coordinator) {
        DictationStarter(context, pipeline, scope, coordinator) { latestOpenNote(it) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        starter.start()
    }

    HomeScreen(
        accessibilityActive = accessibilityActive,
        notes = notes.map { note ->
            RecentNote(
                id = note.id,
                title = note.title,
                snippet = noteSnippet(note.title, note.body),
                time = RelativeTime.format(note.updatedAtMs, nowMs)
            )
        },
        onStatus = { if (accessibilityActive) onOpenDiagnostics() else onOpenOnboarding() },
        onMic = {
            val action = micAction(
                accessibilityRunning = FlowVoiceAccessibilityService.isRunning,
                microphoneGranted = context.hasPermission(Manifest.permission.RECORD_AUDIO),
                sdkInt = Build.VERSION.SDK_INT,
                canDrawOverlays = Settings.canDrawOverlays(context)
            )
            when (action) {
                MicAction.OpenOnboarding -> onOpenOnboarding()
                MicAction.OverlayUnsupported ->
                    context.toast("O ditado sobre outros apps requer Android 8 ou superior.")
                MicAction.RequestOverlayPermission -> {
                    context.toast("Autorize “sobrepor a outros apps” e toque de novo no microfone.")
                    context.startActivitySafely(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                    )
                }
                MicAction.StartDictation -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !askedNotifications &&
                        !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                    ) {
                        askedNotifications = true
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        starter.start()
                    }
                }
            }
        },
        onOpenNotes = onOpenNotes,
        onOpenNote = onOpenNote,
        modifier = modifier
    )
}

private class DictationStarter(
    private val context: Context,
    private val pipeline: DictationPipeline,
    private val scope: CoroutineScope,
    private val notes: NoteDictationCoordinator,
    private val openNote: (String) -> Unit
) {
    fun start() {
        val previous = pipeline.session.value
        val dictatingNote = noteToResumeOnMic(previous.status.isBusy, notes.activeNoteId)
        if (dictatingNote != null) {
            context.toast("Há um ditado de nota em andamento: pare-o na nota.")
            openNote(dictatingNote)
            return
        }
        if (previous.status.isBusy) {
            context.toast("Já há um ditado em andamento.")
            return
        }
        pipeline.clearAbandonedStart()
        ContextCompat.startForegroundService(
            context,
            Intent(context, FlowVoiceOverlayService::class.java)
                .setAction(OverlayStartRequests.ACTION_START_DICTATION)
        )
        scope.launch {
            val status = withTimeoutOrNull(START_TIMEOUT_MS) {
                pipeline.session.mapNotNull { startOutcome(previous, it) }.first()
            }
            when (status) {
                DictationPipelineStatus.Recording -> returnToPreviousApp()
                is DictationPipelineStatus.Failed -> context.toast("Não foi possível iniciar o ditado: ${status.message}")
                null -> {
                    pipeline.abandonStart(previous.id, LATE_START_GRACE_MS)
                    context.toast("O microfone não respondeu a tempo; ditado cancelado.")
                }
                else -> Unit
            }
        }
    }

    // moveTaskToBack sozinho mostra a tarefa de baixo, que no One UI é o launcher mesmo vindo
    // pelos recentes (P130); reabrir o app anotado pelo serviço retoma a tarefa dele.
    private fun returnToPreviousApp() {
        val previousApp = FlowVoiceAccessibilityService.service?.previousAppLaunchIntent()
        // startActivity direto: startActivitySafely marcaria o app de origem como aberto pelo FlowVoice.
        val reopened = previousApp != null && runCatching { context.startActivity(previousApp) }.isSuccess
        if (!reopened) context.findActivity()?.moveTaskToBack(true)
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.toast(message: String) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
}

@Composable
internal fun HomeScreen(
    accessibilityActive: Boolean,
    notes: List<RecentNote>,
    onStatus: () -> Unit,
    onMic: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = modifier
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Wordmark()
            StatusPill(active = accessibilityActive, onClick = onStatus)
        }
        HeroCard(
            onMic = onMic,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val stat = Modifier
                .weight(1f)
                .fillMaxHeight()
            StatCard(value = NO_DATA, label = "Latência média", modifier = stat)
            StatCard(value = NO_DATA, label = "Ditados hoje", modifier = stat)
            StatCard(value = NO_DATA, label = "Gasto hoje", modifier = stat)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoLabel("Últimas notas")
            Text(
                text = "ver todas",
                style = typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.accentText,
                modifier = Modifier
                    .clip(RoundedCornerShape(FlowVoiceRadius.chip))
                    .clickable(role = Role.Button, onClick = onOpenNotes)
                    .heightIn(min = FlowVoiceSpacing.minTouchTarget)
                    .padding(horizontal = 4.dp, vertical = 14.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (notes.isEmpty()) {
                Text(
                    text = "Nenhuma nota ainda. As notas ditadas aparecem aqui.",
                    style = typography.bodyMedium,
                    color = colors.textMuted
                )
            }
            notes.forEach { note ->
                RecentNoteCard(note = note, onClick = { onOpenNote(note.id) })
            }
        }
    }
}

@Composable
private fun HeroCard(onMic: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val shape = RoundedCornerShape(FlowVoiceRadius.hero)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
            .border(1.dp, colors.heroBorder, shape)
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Toque e fale.",
            style = typography.headline,
            color = colors.heroTitle,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "O texto entra no campo que já está aberto, sem trocar seu teclado.",
            style = typography.bodyMedium,
            color = colors.heroSubtitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 220.dp)
        )
        Spacer(Modifier.height(22.dp))
        MicButton(onClick = onMic, contentDescription = "Ditar")
        Spacer(Modifier.height(16.dp))
        MonoLabel("Segure ou toque para ditar", color = colors.textTertiary)
    }
}

@Composable
private fun RecentNoteCard(note: RecentNote, onClick: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    FvCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(FlowVoiceRadius.cardSmall),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = note.title,
                style = typography.itemTitle.copy(fontSize = 14.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = note.time,
                style = typography.monoTimestamp.copy(lineHeight = 1.3.em),
                color = colors.textTimestamp
            )
        }
        if (note.snippet.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = note.snippet,
                style = typography.bodyMedium.copy(lineHeight = 1.4.em),
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(heightDp = 800)
@Composable
private fun HomeScreenPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        HomeScreen(
            accessibilityActive = dark,
            notes = listOf(
                RecentNote("1", "Reunião de orçamento", "Fechamos o escopo da fase 2 com a Marina.", "09:41"),
                RecentNote("2", "Ideias para o F05", "Rodar o corpus pt-BR", "ontem")
            ),
            onStatus = {},
            onMic = {},
            onOpenNotes = {},
            onOpenNote = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
