package dev.rafaelbrauner.flowvoice.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionState
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler

@Composable
fun FlowVoiceDesktopScreen(controller: DesktopDictationController, hideWindow: () -> Unit) {
    val status by controller.status.collectAsState()
    val keyConfigured by controller.keyConfigured.collectAsState()
    val finalText by controller.finalText.collectAsState()
    val sessionState by controller.sessionState.collectAsState()
    val segments by controller.segments.collectAsState()
    var keyDraft by remember { mutableStateOf("") }
    val preview = LivePreviewAssembler.assemble(
        segments = segments,
        sessionComplete = sessionState == DictationSessionState.Finalized
    )

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("FlowVoice — desktop", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = if (controller.insertionAvailable) {
                        "Inserção direta: SendInput (Windows)"
                    } else {
                        "Inserção direta indisponível neste sistema (só Windows)"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                Text("Chave OpenRouter: ${if (keyConfigured) "configurada" else "não configurada"}")
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    label = { Text("Nova chave OpenRouter") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        controller.saveKey(keyDraft)
                        keyDraft = ""
                    },
                    enabled = keyDraft.isNotBlank()
                ) {
                    Text("Validar e salvar chave")
                }

                HorizontalDivider()

                Text("Sessão: ${describe(sessionState)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = controller::startDictation,
                        enabled = keyConfigured && !sessionState.isActive
                    ) {
                        Text("Iniciar ditado")
                    }
                    Button(
                        onClick = controller::finalizeDictation,
                        enabled = sessionState == DictationSessionState.Capturing
                    ) {
                        Text("Finalizar")
                    }
                    OutlinedButton(
                        onClick = controller::cancelDictation,
                        enabled = sessionState.isActive
                    ) {
                        Text("Cancelar")
                    }
                }

                Text("Prévia", style = MaterialTheme.typography.titleSmall)
                if (preview.finalized.isNotBlank()) {
                    Text(preview.finalized, style = MaterialTheme.typography.bodyLarge)
                }
                if (preview.provisional.isNotBlank()) {
                    Text(
                        text = preview.provisional,
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (preview.full.isBlank()) {
                    Text("(vazia)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = { controller.insertIntoActiveApp(hideWindow) },
                    enabled = finalText.isNotBlank()
                ) {
                    Text("Inserir no app ativo (em 3 s)")
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun describe(state: DictationSessionState): String = when (state) {
    DictationSessionState.Idle -> "parada"
    DictationSessionState.Capturing -> "gravando"
    DictationSessionState.Finalizing -> "finalizando"
    DictationSessionState.Finalized -> "finalizada"
    DictationSessionState.Cancelled -> "cancelada"
    is DictationSessionState.Error -> "erro: ${state.message}"
}
