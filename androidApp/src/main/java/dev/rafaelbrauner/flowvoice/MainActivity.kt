package dev.rafaelbrauner.flowvoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionState
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindowAggregator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), KoinComponent {

    private val dictationController by inject<DictationSessionController>()

    private val serviceRunning = mutableStateOf(FlowVoiceAccessibilityService.isRunning)
    private val logLines = mutableStateListOf<String>()
    private val testText = mutableStateOf("")
    private val capturedDurationMs = mutableStateOf(0L)
    private val windowCount = mutableStateOf(0)

    private val dictationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startDictation()
            } else {
                addLog("Permissão de microfone negada.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        observeSessionState()
        observeSessionWindows()

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "FlowVoice",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Text(
                        text = "POC de ditado e inserção direta",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    val sessionState by dictationController.state.collectAsState()

                    Text(
                        text = "Serviço de acessibilidade: ${if (serviceRunning.value) "ativo" else "inativo"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Sessão de ditado: ${describeSessionState(sessionState)}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Microfone: ${if (sessionState is DictationSessionState.Capturing) "gravando" else "inativo"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Duração capturada: ${formatDuration(capturedDurationMs.value)} · Janelas: ${windowCount.value} · Janela alvo: ${formatDuration(DictationWindowAggregator.DEFAULT_TARGET_DURATION_MS)}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = { requestDictationStart() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sessionState.isActive
                    ) {
                        Text("Iniciar ditado", modifier = Modifier.padding(8.dp))
                    }

                    Button(
                        onClick = { finalizeDictation() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sessionState is DictationSessionState.Capturing
                    ) {
                        Text("Finalizar", modifier = Modifier.padding(8.dp))
                    }

                    Button(
                        onClick = { cancelDictation() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sessionState.isActive
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
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
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

    private fun observeSessionState() {
        lifecycleScope.launch {
            dictationController.state.drop(1).collect { state ->
                capturedDurationMs.value = dictationController.capturedDurationMs
                addLog("Estado da sessão: ${describeSessionState(state)}")
            }
        }
    }

    private fun observeSessionWindows() {
        lifecycleScope.launch {
            dictationController.windows.collect { window ->
                windowCount.value += 1
                addLog("Janela ${window.index + 1} criada (${formatDuration(window.durationMs)}).")
            }
        }
    }

    private fun requestDictationStart() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            startDictation()
        } else {
            addLog("Solicitando permissão de microfone.")
            dictationPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startDictation() {
        windowCount.value = 0
        capturedDurationMs.value = 0L
        addLog("Iniciando ditado...")

        lifecycleScope.launch {
            try {
                dictationController.start()
                startCapturedDurationPolling()
            } catch (error: Exception) {
                capturedDurationMs.value = dictationController.capturedDurationMs
                addLog("Falha ao iniciar captura: ${error.message ?: "erro desconhecido"}")
            }
        }
    }

    private fun finalizeDictation() {
        addLog("Finalizando ditado...")

        lifecycleScope.launch {
            try {
                dictationController.finalize()
            } catch (error: Exception) {
                capturedDurationMs.value = dictationController.capturedDurationMs
                addLog("Falha ao finalizar ditado: ${error.message ?: "erro desconhecido"}")
            }
        }
    }

    private fun cancelDictation() {
        addLog("Cancelando ditado...")

        lifecycleScope.launch {
            try {
                dictationController.cancel()
            } catch (error: Exception) {
                capturedDurationMs.value = dictationController.capturedDurationMs
                addLog("Falha ao cancelar ditado: ${error.message ?: "erro desconhecido"}")
            }
        }
    }

    private fun startCapturedDurationPolling() {
        lifecycleScope.launch {
            while (dictationController.state.value is DictationSessionState.Capturing) {
                capturedDurationMs.value = dictationController.capturedDurationMs
                delay(250L)
            }

            capturedDurationMs.value = dictationController.capturedDurationMs
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

    private fun formatDuration(durationMs: Long): String =
        String.format(Locale.getDefault(), "%.1fs", durationMs / 1000.0)

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