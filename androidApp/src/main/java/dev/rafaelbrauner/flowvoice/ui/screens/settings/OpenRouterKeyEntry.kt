package dev.rafaelbrauner.flowvoice.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.shared.model.TranscriptionModelCatalog
import dev.rafaelbrauner.flowvoice.shared.transcription.KeyValidationResult
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterKeyValidator
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStoreUnavailableException
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.screens.diagnostics.DiagnosticsLog
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Validar e guardar a chave OpenRouter: o mesmo caminho em Ajustes e no passo 3 do onboarding.
@Stable
class OpenRouterKeyEntry(
    private val secretStore: SecretStore,
    private val validator: OpenRouterKeyValidator,
    private val modelCatalog: TranscriptionModelCatalog,
    private val diagnosticsLog: DiagnosticsLog,
    private val scope: CoroutineScope
) {
    var maskedKey by mutableStateOf(KeyMask.mask(secretStore.readOpenRouterKey()))
        private set
    var draft by mutableStateOf("")
    var draftVisible by mutableStateOf(false)
    var validating by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun refresh() {
        maskedKey = KeyMask.mask(secretStore.readOpenRouterKey())
    }

    fun save(onSaved: () -> Unit = {}) {
        val candidate = draft
        if (candidate.isBlank() || validating) return
        validating = true
        message = "Validando chave…"
        diagnosticsLog.add("Validando chave OpenRouter.")
        scope.launch {
            var saved = false
            val result = when (validator.validate(candidate)) {
                KeyValidationResult.Valid -> try {
                    secretStore.writeOpenRouterKey(candidate)
                    draft = ""
                    draftVisible = false
                    refresh()
                    modelCatalog.clear()
                    saved = true
                    "Chave validada e guardada cifrada."
                } catch (_: SecretStoreUnavailableException) {
                    "Cofre da chave indisponível neste aparelho; a chave não foi salva."
                }
                KeyValidationResult.InvalidFormat -> OpenRouterKeyValidator.formatMessage(candidate).orEmpty()
                KeyValidationResult.Rejected -> "A OpenRouter recusou esta chave."
                KeyValidationResult.Unavailable -> "Não foi possível validar agora (rede ou serviço)."
            }
            message = result
            diagnosticsLog.add(result)
            validating = false
            if (saved) onSaved()
        }
    }
}

@Composable
fun rememberOpenRouterKeyEntry(): OpenRouterKeyEntry {
    val secretStore = rememberKoin<SecretStore>()
    val validator = rememberKoin<OpenRouterKeyValidator>()
    val modelCatalog = rememberKoin<TranscriptionModelCatalog>()
    val diagnosticsLog = rememberKoin<DiagnosticsLog>()
    val scope = rememberCoroutineScope()
    return remember { OpenRouterKeyEntry(secretStore, validator, modelCatalog, diagnosticsLog, scope) }
}

@Composable
internal fun OpenRouterKeyInput(
    draft: String,
    draftVisible: Boolean,
    validating: Boolean,
    maskedKey: String?,
    message: String?,
    idleHint: String,
    onDraftChange: (String) -> Unit,
    onToggleDraftVisibility: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    alwaysShowSave: Boolean = false
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FvDarkField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = maskedKey ?: "sk-or-v1-…",
                label = "Nova chave OpenRouter",
                visualTransformation = if (draftVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation('•')
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                enabled = !validating
            )
            FvFieldButton(
                text = if (draftVisible) "ocultar" else "mostrar",
                onClick = onToggleDraftVisibility,
                enabled = draft.isNotEmpty()
            )
        }
        if (alwaysShowSave || draft.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = if (validating) "Validando…" else "Validar e salvar",
                onClick = onSave,
                enabled = !validating && draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = message ?: idleHint,
            style = FlowVoiceTheme.typography.bodySmall,
            color = FlowVoiceTheme.colors.textTertiary
        )
    }
}
