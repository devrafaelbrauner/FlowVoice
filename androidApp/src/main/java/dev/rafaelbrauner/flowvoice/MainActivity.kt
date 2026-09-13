package dev.rafaelbrauner.flowvoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.rafaelbrauner.flowvoice.auth.GoogleSignInHelper
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import dev.rafaelbrauner.flowvoice.service.FlowVoiceOverlayService
import dev.rafaelbrauner.flowvoice.shared.auth.AuthGateway
import dev.rafaelbrauner.flowvoice.shared.benchmark.BenchmarkBudget
import dev.rafaelbrauner.flowvoice.shared.benchmark.BenchmarkClip
import dev.rafaelbrauner.flowvoice.shared.benchmark.BenchmarkRunner
import dev.rafaelbrauner.flowvoice.shared.benchmark.PtBrCorpus
import dev.rafaelbrauner.flowvoice.shared.diagnostics.DiagnosticReport
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionState
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindowAggregator
import dev.rafaelbrauner.flowvoice.shared.dictionary.PersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.notes.NoteStore
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import dev.rafaelbrauner.flowvoice.shared.sync.SyncEngine
import dev.rafaelbrauner.flowvoice.shared.transcription.KeyValidationResult
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterKeyValidator
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStoreUnavailableException
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), KoinComponent {

    private val pipeline by inject<DictationPipeline>()
    private val secretStore by inject<SecretStore>()
    private val keyValidator by inject<OpenRouterKeyValidator>()
    private val transcriptionClient by inject<TranscriptionClient>()
    private val openRouterConfig by inject<OpenRouterConfig>()
    private val personalDictionary by inject<PersonalDictionary>()
    private val noteStore by inject<NoteStore>()
    private val preferencesStore by inject<PreferencesStore>()
    private val authGateway by inject<AuthGateway>()
    private val syncEngine by inject<SyncEngine>()

    private val serviceRunning = mutableStateOf(FlowVoiceAccessibilityService.isRunning)
    private val logLines = mutableStateListOf<String>()
    private val testText = mutableStateOf("")
    private val capturedDurationMs = mutableStateOf(0L)
    private val keyDraft = mutableStateOf("")
    private val keyConfigured = mutableStateOf(false)
    private val validatingKey = mutableStateOf(false)
    private val referenceText = mutableStateOf(PtBrCorpus.clips.first().reference)
    private val budgetUsd = mutableStateOf("1.00")
    private val rankingText = mutableStateOf("")
    private val runningBenchmark = mutableStateOf(false)
    private val dictionaryTick = mutableStateOf(0)
    private val newTerm = mutableStateOf("")
    private val notesTick = mutableStateOf(0)
    private val sessionTick = mutableStateOf(0)

    private val dictationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startDictation()
            } else {
                addLog("Permissão de microfone negada.")
            }
        }

    private val overlayPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[Manifest.permission.RECORD_AUDIO] == true) {
                toggleOverlay()
            } else {
                addLog("Botão flutuante precisa da permissão de microfone.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        keyConfigured.value = secretStore.readOpenRouterKey() != null

        observeSessionState()
        observePipeline()

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "FlowVoice",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Text(
                        text = "Onboard: acessibilidade, microfone, chave OpenRouter, Google.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    sessionTick.value
                    val prefs = preferencesStore.read()
                    Text(
                        text = "Google: ${authGateway.currentUser()?.email ?: "não autenticado"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = prefs.googleWebClientId,
                        onValueChange = {
                            preferencesStore.write(prefs.copy(googleWebClientId = it.trim()))
                            sessionTick.value += 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Google Web Client ID") },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Revisão pontuação/ortografia")
                        Switch(
                            checked = prefs.proofreadingEnabled,
                            onCheckedChange = {
                                preferencesStore.write(prefs.copy(proofreadingEnabled = it))
                                sessionTick.value += 1
                            }
                        )
                    }
                    Button(
                        onClick = { signInGoogle() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = authGateway.currentUser() == null
                    ) {
                        Text("Entrar com Google", modifier = Modifier.padding(8.dp))
                    }
                    Button(
                        onClick = {
                            authGateway.signOut()
                            sessionTick.value += 1
                            addLog("Sessão Google encerrada.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = authGateway.currentUser() != null
                    ) {
                        Text("Sair", modifier = Modifier.padding(8.dp))
                    }
                    Button(
                        onClick = { runSync() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = authGateway.currentUser() != null
                    ) {
                        Text("Sincronizar", modifier = Modifier.padding(8.dp))
                    }
                    val overlayRunning by FlowVoiceOverlayService.runningFlow.collectAsState()
                    Button(
                        onClick = { toggleOverlay() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (overlayRunning) "Ocultar botão flutuante" else "Mostrar botão flutuante",
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Button(
                        onClick = { exportDiagnostics() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Exportar diagnóstico", modifier = Modifier.padding(8.dp))
                    }

                    val sessionState by pipeline.sessionState.collectAsState()
                    val segments by pipeline.segments.collectAsState()
                    val sessionWindows by pipeline.sessionWindows.collectAsState()
                    val pipelineStatus by pipeline.status.collectAsState()

                    Text(
                        text = "Serviço de acessibilidade: ${if (serviceRunning.value) "ativo" else "inativo"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Sessão de ditado: ${describeSessionState(sessionState)} · ${describePipelineStatus(pipelineStatus)}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Microfone: ${if (sessionState is DictationSessionState.Capturing) "gravando" else "inativo"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Duração capturada: ${formatDuration(capturedDurationMs.value)} · Janelas: ${sessionWindows.size} · Janela alvo: ${formatDuration(DictationWindowAggregator.DEFAULT_TARGET_DURATION_MS)}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Chave OpenRouter: ${if (keyConfigured.value) "configurada" else "ausente"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = keyDraft.value,
                        onValueChange = { keyDraft.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nova chave OpenRouter") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Button(
                        onClick = { validateAndStoreKey() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !validatingKey.value && keyDraft.value.isNotBlank()
                    ) {
                        Text("Validar e salvar chave", modifier = Modifier.padding(8.dp))
                    }

                    dictionaryTick.value
                    segments
                    val livePreview = pipeline.preview()
                    val finalizedText = livePreview.finalized
                    val provisionalPreview = livePreview.provisional
                    Text(
                        text = "Prévia ao vivo",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = buildAnnotatedString {
                            if (finalizedText.isBlank() && provisionalPreview.isBlank()) {
                                append("—")
                            } else {
                                if (finalizedText.isNotBlank()) {
                                    append(finalizedText)
                                }
                                if (provisionalPreview.isNotBlank()) {
                                    if (finalizedText.isNotBlank()) append(' ')
                                    withStyle(
                                        SpanStyle(
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                        )
                                    ) {
                                        append(provisionalPreview)
                                    }
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Trechos: ${describeSegments(segments)}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Dicionário aprovado: ${
                            personalDictionary.approved().joinToString { it.surface }.ifBlank { "—" }
                        }",
                        style = MaterialTheme.typography.bodySmall
                    )
                    personalDictionary.pending().forEach { term ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = term.surface,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(onClick = { approveTerm(term.surface) }) { Text("Aprovar") }
                            Button(onClick = { rejectTerm(term.surface) }) { Text("Rejeitar") }
                        }
                    }
                    OutlinedTextField(
                        value = newTerm.value,
                        onValueChange = { newTerm.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Novo termo") },
                        singleLine = true
                    )
                    Button(
                        onClick = { approveTerm(newTerm.value) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = newTerm.value.isNotBlank()
                    ) {
                        Text("Aprovar termo", modifier = Modifier.padding(8.dp))
                    }

                    notesTick.value
                    Button(
                        onClick = {
                            savePreviewNote(finalizedText, provisionalPreview)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = finalizedText.isNotBlank() || provisionalPreview.isNotBlank()
                    ) {
                        Text("Salvar prévia como nota", modifier = Modifier.padding(8.dp))
                    }
                    Text(
                        text = "Notas (${noteStore.list().size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    noteStore.list().forEach { note ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = note.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(onClick = { deleteNote(note.id) }) { Text("Apagar") }
                        }
                    }

                    OutlinedTextField(
                        value = referenceText.value,
                        onValueChange = { referenceText.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Texto de referência (pt-BR)") }
                    )

                    OutlinedTextField(
                        value = budgetUsd.value,
                        onValueChange = { budgetUsd.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Teto do benchmark (USD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    val clipReady = sessionWindows.isNotEmpty() && !sessionState.isActive
                    Text(
                        text = "Clipe F05: ${if (clipReady) "${sessionWindows.size} janelas" else "grave e finalize uma sessão"}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = { runBenchmark() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !runningBenchmark.value && clipReady && keyConfigured.value
                    ) {
                        Text("Rodar benchmark rodada 1", modifier = Modifier.padding(8.dp))
                    }

                    if (rankingText.value.isNotBlank()) {
                        Text(
                            text = rankingText.value,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = { requestDictationStart() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !pipelineStatus.isBusy
                    ) {
                        Text("Iniciar ditado", modifier = Modifier.padding(8.dp))
                    }

                    Button(
                        onClick = { finalizeDictation() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = pipelineStatus == DictationPipelineStatus.Recording
                    ) {
                        Text("Finalizar", modifier = Modifier.padding(8.dp))
                    }

                    Button(
                        onClick = { cancelDictation() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = pipelineStatus.isBusy
                    ) {
                        Text("Cancelar", modifier = Modifier.padding(8.dp))
                    }

                    Button(
                        onClick = { openAccessibilitySettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abrir configurações de acessibilidade", modifier = Modifier.padding(8.dp))
                    }

                    Button(
                        onClick = { insertTestText() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inserir texto de teste", modifier = Modifier.padding(8.dp))
                    }

                    Button(
                        onClick = { addLog("Ponto de diagnóstico registrado.") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Registrar diagnóstico", modifier = Modifier.padding(8.dp))
                    }

                    Text(
                        text = "Diagnóstico",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        logLines.forEach { line: String ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceRunning.value = FlowVoiceAccessibilityService.isRunning
        sessionTick.value += 1
    }

    private fun observeSessionState() {
        lifecycleScope.launch {
            pipeline.sessionState.drop(1).collect { state ->
                capturedDurationMs.value = pipeline.capturedDurationMs
                addLog("Estado da sessão: ${describeSessionState(state)}")
                if (state is DictationSessionState.Capturing) {
                    startCapturedDurationPolling()
                }
            }
        }
    }

    private fun observePipeline() {
        lifecycleScope.launch {
            pipeline.events.collect { addLog(it) }
        }
        lifecycleScope.launch {
            pipeline.status.drop(1).collect { status ->
                when (status) {
                    is DictationPipelineStatus.Completed -> {
                        serviceRunning.value = FlowVoiceAccessibilityService.isRunning
                        dictionaryTick.value += 1
                        addLog("Inserção final (${status.text.length} chars) ${status.insertion.summary}")
                        status.warning?.let { addLog("Aviso: $it") }
                    }
                    is DictationPipelineStatus.Failed -> addLog("Falha no ditado: ${status.message}")
                    DictationPipelineStatus.Cancelled -> addLog("Ditado cancelado.")
                    else -> Unit
                }
            }
        }
    }

    private fun requestDictationStart() {
        if (hasMicrophonePermission()) {
            startDictation()
        } else {
            addLog("Solicitando permissão de microfone.")
            dictationPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startDictation() {
        capturedDurationMs.value = 0L
        addLog("Iniciando ditado...")
        pipeline.requestStart()
    }

    private fun finalizeDictation() {
        addLog("Finalizando ditado...")
        pipeline.requestFinalize()
    }

    private fun cancelDictation() {
        addLog("Cancelando ditado...")
        pipeline.requestCancel()
    }

    private fun startCapturedDurationPolling() {
        lifecycleScope.launch {
            while (pipeline.sessionState.value is DictationSessionState.Capturing) {
                capturedDurationMs.value = pipeline.capturedDurationMs
                delay(250L)
            }

            capturedDurationMs.value = pipeline.capturedDurationMs
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        addLog("Abrindo configurações de acessibilidade.")

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            addLog("Ação de configurações de acessibilidade indisponível neste dispositivo.")
        }
    }

    private fun validateAndStoreKey() {
        val draft = keyDraft.value
        validatingKey.value = true
        addLog("Validando chave OpenRouter.")
        lifecycleScope.launch {
            val result = try {
                keyValidator.validate(draft)
            } catch (_: Exception) {
                KeyValidationResult.Unavailable
            }
            if (result == KeyValidationResult.Valid) {
                try {
                    secretStore.writeOpenRouterKey(draft)
                    keyDraft.value = ""
                    keyConfigured.value = true
                    addLog("Chave OpenRouter validada e armazenada.")
                } catch (_: SecretStoreUnavailableException) {
                    addLog("Cofre da chave indisponível neste aparelho; a chave não foi salva.")
                }
            } else {
                addLog("Não foi possível validar a chave.")
            }
            validatingKey.value = false
        }
    }

    private fun runBenchmark() {
        val maxUsd = budgetUsd.value.replace(',', '.').toDoubleOrNull()
        if (maxUsd == null || maxUsd <= 0.0) {
            addLog("Teto de benchmark inválido.")
            return
        }
        val reference = referenceText.value.trim()
        val clipWindows = pipeline.sessionWindows.value
        if (reference.isEmpty() || clipWindows.isEmpty()) {
            addLog("Clipe ou texto de referência ausente.")
            return
        }
        val clip = BenchmarkClip(
            id = "sessao",
            reference = reference,
            window = mergeWindows(clipWindows)
        )
        runningBenchmark.value = true
        rankingText.value = ""
        addLog("Benchmark rodada 1 com teto $maxUsd USD.")
        lifecycleScope.launch {
            try {
                val report = BenchmarkRunner(
                    client = transcriptionClient,
                    apiKeyProvider = { secretStore.readOpenRouterKey() },
                    eventLog = { event, metadata ->
                        addLog(
                            buildString {
                                append(event)
                                metadata.forEach { (key, value) ->
                                    append(' ')
                                    append(key)
                                    append('=')
                                    append(value)
                                }
                            }
                        )
                    }
                ).run(listOf(clip), BenchmarkBudget(maxUsd = maxUsd))
                rankingText.value = report.rankingText()
                addLog("Benchmark concluído. padrão=${report.defaultModel ?: "n/d"} fallback=${report.fallbackModel ?: "n/d"}")
            } catch (error: Exception) {
                addLog("Falha no benchmark: ${error.message ?: "erro desconhecido"}")
            } finally {
                runningBenchmark.value = false
            }
        }
    }

    private fun mergeWindows(windows: List<DictationWindow>): DictationWindow {
        val first = windows.first()
        val last = windows.last()
        var total = 0
        windows.forEach { total += it.pcm.size }
        val pcm = ByteArray(total)
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
            finishedAtMs = last.finishedAtMs
        )
    }

    private fun savePreviewNote(finalized: String, provisional: String) {
        val body = listOf(finalized, provisional).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (body.isEmpty()) {
            addLog("Nada para salvar na nota.")
            return
        }
        val note = noteStore.create(body)
        notesTick.value += 1
        addLog("Nota salva (${note.body.length} chars).")
    }

    private fun deleteNote(id: String) {
        if (noteStore.delete(id)) {
            notesTick.value += 1
            addLog("Nota apagada.")
        }
    }

    private fun approveTerm(surface: String) {
        val trimmed = surface.trim()
        if (trimmed.isEmpty()) return
        personalDictionary.approve(trimmed)
        newTerm.value = ""
        dictionaryTick.value += 1
        addLog("Termo aprovado (${trimmed.length} chars).")
    }

    private fun rejectTerm(surface: String) {
        personalDictionary.reject(surface)
        dictionaryTick.value += 1
        addLog("Termo rejeitado.")
    }

    private fun insertTestText() {
        serviceRunning.value = FlowVoiceAccessibilityService.isRunning
        val service = FlowVoiceAccessibilityService.service

        if (service == null) {
            addLog("Serviço de acessibilidade inativo. Ative FlowVoice e tente novamente.")
            return
        }

        testText.value = "FlowVoice POC: texto inserido direto na tela."
        val result = service.insertDirect(testText.value)
        addLog(result.summary)
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logLines.add(0, "[$timestamp] $message")
        if (logLines.size > MAX_LOG_LINES) {
            logLines.removeAt(logLines.lastIndex)
        }
    }

    private fun describeSessionState(state: DictationSessionState): String = when (state) {
        is DictationSessionState.Idle -> "inativa"
        is DictationSessionState.Capturing -> "capturando"
        is DictationSessionState.Finalizing -> "finalizando"
        is DictationSessionState.Finalized -> "finalizada"
        is DictationSessionState.Cancelled -> "cancelada"
        is DictationSessionState.Error -> "erro: ${state.message}"
    }

    private fun describePipelineStatus(status: DictationPipelineStatus): String = when (status) {
        DictationPipelineStatus.Idle -> "pronto"
        DictationPipelineStatus.Starting -> "iniciando"
        DictationPipelineStatus.Recording -> "gravando"
        DictationPipelineStatus.Transcribing -> "transcrevendo"
        is DictationPipelineStatus.Completed -> if (status.insertion.success) "inserido" else "não inserido"
        DictationPipelineStatus.Cancelled -> "cancelado"
        is DictationPipelineStatus.Failed -> "falhou"
    }

    private fun formatDuration(durationMs: Long): String =
        String.format(Locale.getDefault(), "%.1fs", durationMs / 1000.0)

    private fun describeSegments(segments: List<TranscriptionSegment>): String {
        if (segments.isEmpty()) return "—"
        return segments.joinToString(separator = " · ") { segment ->
            val label = when (segment.status) {
                TranscriptionSegment.Status.Transcribing -> "transcrevendo"
                TranscriptionSegment.Status.Ok -> "ok"
                TranscriptionSegment.Status.Failed -> segment.errorKind ?: "falha"
            }
            "${segment.windowIndex + 1}:$label"
        }
    }

    private fun signInGoogle() {
        val clientId = preferencesStore.read().googleWebClientId
        if (clientId.isBlank()) {
            addLog("Configure o client ID Google nas preferências.")
            return
        }
        lifecycleScope.launch {
            try {
                val user = GoogleSignInHelper.signIn(this@MainActivity, clientId)
                authGateway.signIn(user)
                sessionTick.value += 1
                addLog("Google autenticado.")
            } catch (error: Exception) {
                addLog("Falha no Google Sign-In.")
            }
        }
    }

    private fun runSync() {
        lifecycleScope.launch {
            val result = syncEngine.sync()
            sessionTick.value += 1
            notesTick.value += 1
            dictionaryTick.value += 1
            addLog("Sync pushed=${result.pushed} notas=${result.mergedNotes} termos=${result.mergedTerms}")
        }
    }

    private fun toggleOverlay() {
        if (FlowVoiceOverlayService.running) {
            stopService(Intent(this, FlowVoiceOverlayService::class.java))
            addLog("Overlay desligado.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            addLog("Botão flutuante requer Android 8 ou superior.")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            addLog("Autorize sobreposição para o botão flutuante.")
            return
        }
        if (!hasMicrophonePermission()) {
            val permissions = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            overlayPermissionsLauncher.launch(permissions.toTypedArray())
            return
        }
        startOverlay()
    }

    private fun startOverlay() {
        ContextCompat.startForegroundService(this, Intent(this, FlowVoiceOverlayService::class.java))
        addLog("Overlay ligado.")
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun exportDiagnostics() {
        val report = DiagnosticReport.build(
            generatedAtMs = System.currentTimeMillis(),
            accessibility = FlowVoiceAccessibilityService.isRunning,
            microphoneGranted = hasMicrophonePermission(),
            keyConfigured = secretStore.readOpenRouterKey() != null,
            signedIn = authGateway.isSignedIn,
            proofreading = preferencesStore.read().proofreadingEnabled,
            model = openRouterConfig.model,
            notes = noteStore.list().size,
            terms = personalDictionary.approved().size,
            overlay = FlowVoiceOverlayService.running
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report.asText())
        }
        startActivity(Intent.createChooser(share, "Exportar diagnóstico"))
        addLog("Diagnóstico exportado (${report.lines.size} linhas).")
    }

    companion object {
        private const val MAX_LOG_LINES = 20
    }
}

@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    MaterialTheme {
        Text(text = "FlowVoice POC")
    }
}
