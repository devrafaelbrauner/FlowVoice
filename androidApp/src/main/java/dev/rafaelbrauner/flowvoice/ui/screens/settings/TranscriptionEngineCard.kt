package dev.rafaelbrauner.flowvoice.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.localasr.LocalModelState
import dev.rafaelbrauner.flowvoice.localasr.ModelDiskSpace
import dev.rafaelbrauner.flowvoice.shared.localasr.NemotronModel
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
import dev.rafaelbrauner.flowvoice.ui.components.FvDivider
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.components.PillButtonVariant
import dev.rafaelbrauner.flowvoice.ui.components.SettingsRow
import dev.rafaelbrauner.flowvoice.ui.icons.FlowVoiceIcons
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

private val ARCHIVE_SIZE = ModelDiskSpace.megabytes(NemotronModel.ARCHIVE_BYTES)
private val INSTALLED_SIZE = ModelDiskSpace.megabytes(NemotronModel.INSTALLED_BYTES)
private val REQUIRED_SPACE = ModelDiskSpace.gigabytes(NemotronModel.REQUIRED_FREE_BYTES)

// Escolha do motor + instalação do modelo do aparelho. Vai dentro de um SettingsCard, antes do
// modelo da nuvem.
@Composable
internal fun TranscriptionEngineSection(state: SettingsUiState, actions: SettingsActions) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val installed = state.localModel == LocalModelState.Installed
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
        MonoLabel("Motor de transcrição")
        Spacer(Modifier.height(4.dp))
        Text(
            text = engineStatus(state.engineChoice, state.effectiveEngine),
            style = typography.bodySmall,
            color = colors.textTertiary
        )
    }
    EngineOption(
        label = "No aparelho (Nemotron, ao vivo)",
        hint = when {
            !installed -> "Baixe o modelo abaixo para usar"
            // Duas passadas: com a revisão final por IA, o áudio do ditado vai à nuvem no fim.
            state.proofreadingEnabled -> "Ao vivo no celular; com a revisão final por IA, o áudio vai à OpenRouter no fim"
            else -> "Sem internet e sem custo; o áudio não sai do celular"
        },
        selected = state.engineChoice == TranscriptionEngine.Local,
        enabled = installed,
        onClick = { actions.onEngineSelect(TranscriptionEngine.Local) }
    )
    FvDivider()
    EngineOption(
        label = "Nuvem (OpenRouter)",
        hint = if (state.keyConfigured) "Envia o áudio à OpenRouter" else "Envia o áudio à OpenRouter; precisa da chave",
        selected = state.engineChoice == TranscriptionEngine.Cloud,
        enabled = true,
        onClick = { actions.onEngineSelect(TranscriptionEngine.Cloud) }
    )
    FvDivider()
    LocalModelPanel(state, actions)
}

private fun engineStatus(choice: TranscriptionEngine, effective: TranscriptionEngine): String = when {
    effective == TranscriptionEngine.Local -> "Os ditados são transcritos no aparelho."
    choice == TranscriptionEngine.Local -> "O modelo não está no aparelho: os ditados vão para a nuvem até ele ser baixado."
    else -> "Os ditados são transcritos na nuvem (OpenRouter)."
}

@Composable
private fun EngineOption(label: String, hint: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    SettingsRow(
        label = label,
        hint = hint,
        onClick = if (enabled && !selected) onClick else null,
        trailing = if (selected) {
            {
                Icon(
                    FlowVoiceIcons.Check,
                    contentDescription = "Motor escolhido",
                    tint = colors.accentText,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        }
    )
}

@Composable
private fun LocalModelPanel(state: SettingsUiState, actions: SettingsActions) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    var confirmingDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MonoLabel("Modelo do aparelho")
        when (val model = state.localModel) {
            LocalModelState.NotInstalled -> {
                PanelText(
                    "Funciona sem internet e sem custo; o áudio não sai do celular (com a revisão final por IA " +
                        "ligada, vai à OpenRouter no fim do ditado). Baixado do GitHub " +
                        "(k2-fsa/sherpa-onnx); precisa de $REQUIRED_SPACE livres durante a instalação e " +
                        "ocupa $INSTALLED_SIZE. Ao terminar, os ditados passam a usar o aparelho."
                )
                PillButton(
                    text = "Baixar o modelo ($ARCHIVE_SIZE)",
                    onClick = actions.onModelInstall,
                    height = 34.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is LocalModelState.Downloading -> {
                val fraction = model.downloadedBytes.toFloat() / model.totalBytes.coerceAtLeast(1)
                PanelText(
                    "Baixando… ${ModelDiskSpace.megabytes(model.downloadedBytes)} de " +
                        "${ModelDiskSpace.megabytes(model.totalBytes)} · ${(fraction * 100).toInt()} %"
                )
                ProgressBar(fraction)
                CancelButton(actions)
            }
            LocalModelState.Verifying -> {
                PanelText("Conferindo…")
                ProgressBar(1f)
                CancelButton(actions)
            }
            is LocalModelState.Extracting -> {
                PanelText("Extraindo… ${(model.progress * 100).toInt()} %")
                ProgressBar(model.progress)
                CancelButton(actions)
            }
            is LocalModelState.Failed -> {
                Text(text = model.message, style = typography.bodySmall, color = colors.destructiveText)
                PillButton(
                    text = "Tentar de novo",
                    onClick = actions.onModelInstall,
                    height = 34.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LocalModelState.Installed -> if (confirmingDelete) {
                PanelText(
                    if (state.engineChoice == TranscriptionEngine.Local) {
                        "Apagar o modelo? Os ditados voltam para a nuvem (OpenRouter); para usar o aparelho " +
                            "de novo, é preciso baixar $ARCHIVE_SIZE outra vez."
                    } else {
                        "Apagar o modelo? Para usar o aparelho de novo, é preciso baixar $ARCHIVE_SIZE outra vez."
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        text = "Manter",
                        onClick = { confirmingDelete = false },
                        variant = PillButtonVariant.Outline,
                        height = 34.dp,
                        modifier = Modifier.weight(1f)
                    )
                    PillButton(
                        text = "Apagar",
                        onClick = {
                            confirmingDelete = false
                            actions.onModelDelete()
                        },
                        variant = PillButtonVariant.Destructive,
                        height = 34.dp,
                        enabled = !state.dictationBusy,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                PanelText(
                    if (state.dictationBusy) {
                        "No aparelho · ocupa $INSTALLED_SIZE. Termine o ditado para poder apagar."
                    } else {
                        "No aparelho · ocupa $INSTALLED_SIZE."
                    }
                )
                PillButton(
                    text = "Apagar modelo",
                    onClick = { confirmingDelete = true },
                    variant = PillButtonVariant.Outline,
                    height = 34.dp,
                    enabled = !state.dictationBusy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PanelText(text: String) {
    Text(text = text, style = FlowVoiceTheme.typography.bodySmall, color = FlowVoiceTheme.colors.textTertiary)
}

@Composable
private fun CancelButton(actions: SettingsActions) {
    PillButton(
        text = "Cancelar",
        onClick = actions.onModelCancel,
        variant = PillButtonVariant.Outline,
        height = 34.dp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ProgressBar(fraction: Float) {
    val colors = FlowVoiceTheme.colors
    val value = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.hairline)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }
    ) {
        Box(
            Modifier
                .fillMaxWidth(value)
                .fillMaxHeight()
                .background(colors.accent)
        )
    }
}
