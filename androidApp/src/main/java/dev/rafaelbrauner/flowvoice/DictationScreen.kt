package dev.rafaelbrauner.flowvoice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.rafaelbrauner.flowvoice.shared.security.sanitizeApiKey
import dev.rafaelbrauner.flowvoice.shared.session.SessionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DictationScreen(
    logLines: MutableList<String>,
    addLog: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dictState by DictationForegroundService.state.collectAsState()
    val dictError by DictationForegroundService.error.collectAsState()
    val liveText by DictationForegroundService.liveText.collectAsState()
    val lastResult by DictationForegroundService.lastResult.collectAsState()
    val lastReport by DictationForegroundService.lastReport.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    var keyLoaded by remember { mutableStateOf(false) }
    var selectedModel by remember {
        mutableStateOf(dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionManager.DEFAULT_MODEL)
    }
    var availableModels by remember { mutableStateOf(listOf(selectedModel)) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        addLog(if (granted) "Permissão de microfone concedida." else "Permissão de microfone negada.")
    }

    LaunchedEffect(Unit) {
        val stored = ServiceLocator.apiKeyStore(context).loadApiKey()
        if (!stored.isNullOrBlank()) {
            apiKey = stored
            keyLoaded = true
        }
        selectedModel = ServiceLocator.modelStore(context).loadModel()
            ?: dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionManager.DEFAULT_MODEL
    }

    LaunchedEffect(dictError) {
        dictError?.let { addLog("Ditado: $it") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "FlowVoice — Ditado",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = when (dictState) {
                SessionState.LISTENING -> "Ouvindo… fale agora"
                SessionState.WORKING -> "Transcrevendo…"
                SessionState.IDLE -> "Ocioso"
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )

        if (!micGranted) {
            OutlinedButton(
                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Permitir microfone (fale para ditar)")
            }
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Chave OpenRouter") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val clean = sanitizeApiKey(apiKey)
                        if (clean == null) {
                            addLog("Chave inválida — verifique e tente de novo.")
                            return@launch
                        }
                        try {
                            val models = ServiceLocator.openRouterApi()
                                .listTranscriptionModels(clean)
                                .map { it.id }
                            if (models.isNotEmpty()) availableModels = models
                            if (selectedModel !in models && models.isNotEmpty()) {
                                selectedModel = models.first()
                            }
                            ServiceLocator.apiKeyStore(context).saveApiKey(clean)
                            ServiceLocator.modelStore(context).saveModel(selectedModel)
                            apiKey = clean
                            keyLoaded = true
                            addLog("Chave validada e salva com segurança.")
                        } catch (e: Exception) {
                            addLog("Falha ao validar chave: ${e.message}")
                        }
                    }
                },
                modifier = Modifier.weight(1f).padding(top = 8.dp)
            ) {
                Text("Validar e salvar")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        ServiceLocator.apiKeyStore(context).clearApiKey()
                        apiKey = ""
                        keyLoaded = false
                        addLog("Chave removida deste aparelho.")
                    }
                },
                modifier = Modifier.weight(1f).padding(top = 8.dp)
            ) {
                Text("Remover chave")
            }
        }
        if (keyLoaded) {
            Text(
                text = "Chave configurada ✓",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (availableModels.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Modelo: $selectedModel")
            }
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableModels.take(30).forEach { model ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            selectedModel = model
                            expanded = false
                            scope.launch {
                                ServiceLocator.modelStore(context).saveModel(model)
                                addLog("Modelo selecionado: $model")
                            }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { DictationForegroundService.start(context) },
                enabled = dictState == SessionState.IDLE && micGranted,
                modifier = Modifier.weight(1f).padding(top = 12.dp)
            ) {
                Text("Ditar")
            }
            Button(
                onClick = { DictationForegroundService.stop(context) },
                enabled = dictState == SessionState.LISTENING,
                modifier = Modifier.weight(1f).padding(top = 12.dp)
            ) {
                Text("Parar")
            }
            OutlinedButton(
                onClick = { DictationForegroundService.cancel(context) },
                enabled = dictState != SessionState.IDLE,
                modifier = Modifier.weight(1f).padding(top = 12.dp)
            ) {
                Text("Cancelar")
            }
        }

        if (dictState == SessionState.LISTENING) {
            if (liveText.tentative.isNotBlank() || liveText.committed.isNotBlank()) {
                Text(
                    text = buildString {
                        append(liveText.committed)
                        if (liveText.committed.isNotBlank() && liveText.tentative.isNotBlank()) append(" ")
                        append(liveText.tentative)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                Text(
                    text = "Prévia provisória — o texto final pode mudar",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = "Ouvindo… a prévia aparece em alguns segundos",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        if (lastResult.isNotBlank()) {
            Text(
                text = "Último ditado: $lastResult",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            lastReport?.let { report ->
                Text(
                    text = "Janelas=${report.windows} modelo=${report.modelId} " +
                        "latência=${report.totalLatencyMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Text(
            text = "Diagnóstico",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 20.dp, bottom = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            logLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}
