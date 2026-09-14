package dev.rafaelbrauner.flowvoice.ui.screens.diagnostics

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import dev.rafaelbrauner.flowvoice.service.FlowVoiceOverlayService
import dev.rafaelbrauner.flowvoice.shared.auth.AuthGateway
import dev.rafaelbrauner.flowvoice.shared.benchmark.BenchmarkBudget
import dev.rafaelbrauner.flowvoice.shared.benchmark.BenchmarkClip
import dev.rafaelbrauner.flowvoice.shared.benchmark.BenchmarkRunner
import dev.rafaelbrauner.flowvoice.shared.benchmark.PtBrCorpus
import dev.rafaelbrauner.flowvoice.shared.diagnostics.DiagnosticReport
import dev.rafaelbrauner.flowvoice.shared.dictionary.PersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.notes.NoteStore
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.ui.components.FvCard
import dev.rafaelbrauner.flowvoice.ui.components.FvDivider
import dev.rafaelbrauner.flowvoice.ui.components.KeyValueRow
import dev.rafaelbrauner.flowvoice.ui.components.LogBlock
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.components.PillButtonVariant
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.screens.settings.FvDarkField
import dev.rafaelbrauner.flowvoice.ui.screens.settings.FvTextAction
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val rows: List<Pair<String, String>>,
    val accessibilityActive: Boolean,
    val exported: Boolean,
    val logLines: List<String>,
    val budgetUsd: String,
    val referenceText: String,
    val clipStatus: String,
    val canRunBenchmark: Boolean,
    val runningBenchmark: Boolean,
    val ranking: String
)

data class DiagnosticsActions(
    val onBack: () -> Unit,
    val onExport: () -> Unit,
    val onOpenAccessibilitySettings: () -> Unit,
    val onBudgetChange: (String) -> Unit,
    val onReferenceChange: (String) -> Unit,
    val onRunBenchmark: () -> Unit
)

private const val NO_SOURCE = "—"

@Composable
fun DiagnosticsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secretStore = rememberKoin<SecretStore>()
    val authGateway = rememberKoin<AuthGateway>()
    val preferencesStore = rememberKoin<PreferencesStore>()
    val config = rememberKoin<OpenRouterConfig>()
    val noteStore = rememberKoin<NoteStore>()
    val dictionary = rememberKoin<PersonalDictionary>()
    val pipeline = rememberKoin<DictationPipeline>()
    val transcriptionClient = rememberKoin<TranscriptionClient>()
    val diagnosticsLog = rememberKoin<DiagnosticsLog>()

    val logLines by diagnosticsLog.lines.collectAsState()
    val sessionWindows by pipeline.sessionWindows.collectAsState()
    val sessionState by pipeline.sessionState.collectAsState()
    var accessibilityActive by remember { mutableStateOf(FlowVoiceAccessibilityService.isRunning) }
    var serviceFlags by remember { mutableStateOf(currentServiceFlags()) }
    var keyConfigured by remember { mutableStateOf(secretStore.readOpenRouterKey() != null) }
    var exported by remember { mutableStateOf(false) }
    var budgetUsd by rememberSaveable { mutableStateOf("1.00") }
    var referenceText by rememberSaveable { mutableStateOf(PtBrCorpus.clips.first().reference) }
    var ranking by remember { mutableStateOf("") }
    var runningBenchmark by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        accessibilityActive = FlowVoiceAccessibilityService.isRunning
        serviceFlags = currentServiceFlags()
        keyConfigured = secretStore.readOpenRouterKey() != null
        onPauseOrDispose { }
    }

    fun exportReport() {
        val report = DiagnosticReport.build(
            generatedAtMs = System.currentTimeMillis(),
            accessibility = FlowVoiceAccessibilityService.isRunning,
            microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            keyConfigured = secretStore.readOpenRouterKey() != null,
            signedIn = authGateway.isSignedIn,
            proofreading = preferencesStore.read().proofreadingEnabled,
            model = config.model,
            notes = noteStore.list().size,
            terms = dictionary.approved().size,
            overlay = FlowVoiceOverlayService.running
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report.asText())
        }
        try {
            context.startActivity(Intent.createChooser(share, "Exportar diagnóstico"))
            exported = true
            diagnosticsLog.add("Diagnóstico exportado (${report.lines.size} linhas).")
        } catch (_: ActivityNotFoundException) {
            diagnosticsLog.add("Nenhum app disponível para exportar o diagnóstico.")
        }
    }

    fun runBenchmark() {
        val maxUsd = budgetUsd.replace(',', '.').toDoubleOrNull()
        if (maxUsd == null || maxUsd <= 0.0) {
            diagnosticsLog.add("Teto de benchmark inválido.")
            return
        }
        val reference = referenceText.trim()
        val windows = pipeline.sessionWindows.value
        if (reference.isEmpty() || windows.isEmpty() || runningBenchmark) {
            diagnosticsLog.add("Clipe ou texto de referência ausente.")
            return
        }
        val clip = BenchmarkClip(id = "sessao", reference = reference, window = mergeWindows(windows))
        runningBenchmark = true
        ranking = ""
        diagnosticsLog.add("Benchmark rodada 1 com teto $maxUsd USD.")
        scope.launch {
            try {
                val report = BenchmarkRunner(
                    client = transcriptionClient,
                    apiKeyProvider = { secretStore.readOpenRouterKey() },
                    eventLog = { event, metadata ->
                        diagnosticsLog.add(metadata.entries.joinToString(" ", prefix = "$event ") { "${it.key}=${it.value}" })
                    }
                ).run(listOf(clip), BenchmarkBudget(maxUsd = maxUsd))
                ranking = report.rankingText()
                diagnosticsLog.add(
                    "Benchmark concluído. padrão=${report.defaultModel ?: "n/d"} fallback=${report.fallbackModel ?: "n/d"}"
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnosticsLog.add("Falha no benchmark (${error::class.simpleName}).")
            } finally {
                runningBenchmark = false
            }
        }
    }

    val clipReady = sessionWindows.isNotEmpty() && !sessionState.isActive
    val state = DiagnosticsUiState(
        rows = listOf(
            "aparelho" to Build.MODEL,
            "rota principal" to "commitText",
            "fallback" to "ACTION_SET_TEXT",
            "flags" to serviceFlags,
            "latência p50 / p95" to NO_SOURCE,
            "último alvo" to NO_SOURCE
        ),
        accessibilityActive = accessibilityActive,
        exported = exported,
        logLines = logLines,
        budgetUsd = budgetUsd,
        referenceText = referenceText,
        clipStatus = when {
            !keyConfigured -> "Configure a chave OpenRouter em Ajustes para rodar o benchmark."
            clipReady -> "Clipe: última sessão de ditado (${sessionWindows.size} janelas)."
            else -> "Grave e finalize um ditado para usar como clipe."
        },
        canRunBenchmark = keyConfigured && clipReady && !runningBenchmark,
        runningBenchmark = runningBenchmark,
        ranking = ranking
    )
    val actions = DiagnosticsActions(
        onBack = onBack,
        onExport = ::exportReport,
        onOpenAccessibilitySettings = { openAccessibilitySettings(context, diagnosticsLog) },
        onBudgetChange = { budgetUsd = it },
        onReferenceChange = { referenceText = it },
        onRunBenchmark = ::runBenchmark
    )
    DiagnosticsContent(state = state, actions = actions, modifier = modifier)
}

@Composable
internal fun DiagnosticsContent(
    state: DiagnosticsUiState,
    actions: DiagnosticsActions,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FvTextAction(text = "← ajustes", onClick = actions.onBack)
            AccentOutlinePill(
                text = if (state.exported) "relatório exportado ✓" else "Exportar relatório",
                onClick = actions.onExport
            )
        }
        Text(
            text = "Diagnóstico",
            style = typography.screenTitleSecondary,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp)
        )
        DiagnosticsTable(
            rows = state.rows,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
        if (!state.accessibilityActive) {
            PillButton(
                text = "Abrir configurações de acessibilidade",
                onClick = actions.onOpenAccessibilitySettings,
                variant = PillButtonVariant.Outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )
        }
        MonoLabel(
            text = "Log do serviço",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
        LogBlock(
            lines = state.logLines.ifEmpty { listOf("Sem eventos desde que o app abriu.") },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
        )
        MonoLabel(
            text = "Benchmark F05",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)
        )
        BenchmarkCard(
            state = state,
            actions = actions,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DiagnosticsTable(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = FlowVoiceTheme.colors
    val shape = RoundedCornerShape(FlowVoiceRadius.cardSmall)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
    ) {
        rows.forEachIndexed { index, (key, value) ->
            if (index > 0) FvDivider()
            KeyValueRow(key = key, value = value, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
        }
    }
}

@Composable
private fun BenchmarkCard(state: DiagnosticsUiState, actions: DiagnosticsActions, modifier: Modifier = Modifier) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    FvCard(modifier = modifier.fillMaxWidth()) {
        Text("Teto (USD)", style = typography.bodySmall, color = colors.textTertiary)
        Spacer(Modifier.height(6.dp))
        FvDarkField(
            value = state.budgetUsd,
            onValueChange = actions.onBudgetChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.height(10.dp))
        Text("Texto de referência (pt-BR)", style = typography.bodySmall, color = colors.textTertiary)
        Spacer(Modifier.height(6.dp))
        FvDarkField(
            value = state.referenceText,
            onValueChange = actions.onReferenceChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            textStyle = typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(state.clipStatus, style = typography.bodySmall, color = colors.textTertiary)
        Spacer(Modifier.height(10.dp))
        PillButton(
            text = if (state.runningBenchmark) "Rodando rodada 1…" else "Rodar rodada 1",
            onClick = actions.onRunBenchmark,
            enabled = state.canRunBenchmark,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.ranking.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(state.ranking, style = typography.log, color = colors.textSecondary)
        }
    }
}

@Composable
private fun AccentOutlinePill(text: String, onClick: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .heightIn(min = FlowVoiceSpacing.minTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(shape)
                .background(if (pressed || hovered) colors.accentSurface else Color.Transparent)
                .border(1.dp, colors.accentBorder, shape)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = FlowVoiceTheme.typography.buttonSecondary.copy(fontSize = 12.sp),
                color = colors.accentText
            )
        }
    }
}

private fun currentServiceFlags(): String =
    FlowVoiceAccessibilityService.service?.serviceInfo?.flags?.let { "0x" + Integer.toHexString(it) } ?: NO_SOURCE

private fun openAccessibilitySettings(context: Context, log: DiagnosticsLog) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        log.add("Abrindo configurações de acessibilidade.")
    } catch (_: ActivityNotFoundException) {
        log.add("Configurações de acessibilidade indisponíveis neste aparelho.")
    }
}

@Preview
@Composable
private fun DiagnosticsContentPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        DiagnosticsContent(
            state = DiagnosticsUiState(
                rows = listOf(
                    "aparelho" to "SM-S948B",
                    "rota principal" to "commitText",
                    "fallback" to "ACTION_SET_TEXT",
                    "flags" to "0x8001",
                    "latência p50 / p95" to NO_SOURCE,
                    "último alvo" to NO_SOURCE
                ),
                accessibilityActive = true,
                exported = false,
                logLines = listOf(
                    "[09:42:04] dictation_finalized chars=58 inserted=true",
                    "[09:41:58] dictation_window window=3 durationMs=4000 model=openai/gpt-4o-mini-transcribe",
                    "[09:41:40] dictation_started"
                ),
                budgetUsd = "1.00",
                referenceText = "O médico pediu o exame de sangue para amanhã de manhã.",
                clipStatus = "Clipe: última sessão de ditado (3 janelas).",
                canRunBenchmark = true,
                runningBenchmark = false,
                ranking = ""
            ),
            actions = DiagnosticsActions({}, {}, {}, {}, {}, {})
        )
    }
}
