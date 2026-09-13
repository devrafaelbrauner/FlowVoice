package dev.rafaelbrauner.flowvoice

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val serviceRunning = mutableStateOf(FlowVoiceAccessibilityService.isRunning)
    private val logLines = mutableStateListOf<String>()
    private val testText = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addLog("App inicializado.")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "FlowVoice — POC de inserção direta",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = if (serviceRunning.value) {
                                "Serviço de acessibilidade: ativo"
                            } else {
                                "Serviço de acessibilidade: inativo"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        )

                        OutlinedButton(
                            onClick = { openAccessibilitySettings() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Abrir configurações de acessibilidade")
                        }

                        TextField(
                            value = testText.value,
                            onValueChange = { testText.value = it },
                            label = { Text("Campo de teste") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp)
                                .padding(top = 16.dp),
                        )

                        Button(
                            onClick = { insertDirect() },
                            enabled = serviceRunning.value,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            Text("Inserir via commitText")
                        }

                        Button(
                            onClick = { insertFallback() },
                            enabled = serviceRunning.value,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Text("Inserir via ACTION_SET_TEXT")
                        }

                        OutlinedButton(
                            onClick = { diagnoseFocusedField() },
                            enabled = serviceRunning.value,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Text("Diagnóstico do campo focado")
                        }

                        OutlinedButton(
                            onClick = { clearTestField() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Text("Limpar campo")
                        }

                        Text(
                            text = "Diagnóstico",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(top = 20.dp, bottom = 4.dp),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            logLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceRunning.value = FlowVoiceAccessibilityService.isRunning
        addLog(
            "Estado atualizado: serviço ${
                if (serviceRunning.value) "ativo" else "inativo"
            }"
        )
    }

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            addLog("Abertas as configurações de acessibilidade.")
        }
            .onFailure {
                addLog("Falha ao abrir as configurações: ${it.message}")
            }
    }

    private fun insertDirect() {
        val service = FlowVoiceAccessibilityService.service
            ?: return addLog("Serviço de acessibilidade inativo.")

        val sample = testText.value.ifBlank { "POC FlowVoice — " }
        addLog(service.insertDirect(sample).summary)
    }

    private fun insertFallback() {
        val service = FlowVoiceAccessibilityService.service
            ?: return addLog("Serviço de acessibilidade inativo.")

        val sample = testText.value.ifBlank { "POC FlowVoice — " }
        addLog(service.insertFallback(sample).summary)
    }

    private fun diagnoseFocusedField() {
        val service = FlowVoiceAccessibilityService.service
            ?: return addLog("Serviço de acessibilidade inativo.")

        addLog(service.diagnoseFocusedField().summary)
    }

    private fun clearTestField() {
        testText.value = ""
        addLog("Campo de teste limpo.")
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logLines.add(0, "[$timestamp] $message")
        if (logLines.size > 80) {
            logLines.removeAt(logLines.lastIndex)
        }
    }
}