package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.icons.FlowVoiceIcons
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = onClick != null && (pressed || hovered)
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FlowVoiceSpacing.minTouchTarget)
            .background(if (highlighted) colors.rowHighlight else Color.Transparent)
            .then(clickable)
            .semantics(mergeDescendants = true) {}
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = typography.rowLabel, color = colors.textPrimary)
            if (hint != null) {
                Spacer(Modifier.height(3.dp))
                Text(hint, style = typography.bodySmall, color = colors.textTertiary)
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = typography.monoValue,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = FlowVoiceTheme.colors
    val shape = RoundedCornerShape(FlowVoiceRadius.card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape),
        content = content
    )
}

@Preview
@Composable
private fun SettingsRowPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        SettingsCard {
            SettingsRow("Modelo de transcrição", hint = "Padrão escolhido no benchmark F05", value = "gpt-4o-mini-transcribe", onClick = {})
            FvDivider()
            SettingsRow("Revisão por IA", hint = "Pontuação e ortografia antes de inserir") {
                FvToggle(checked = true, onCheckedChange = {})
            }
            FvDivider()
            SettingsRow("Diagnóstico técnico", hint = "Rota de inserção, permissões, latências", onClick = {}) {
                Icon(
                    FlowVoiceIcons.ChevronRight,
                    contentDescription = null,
                    tint = FlowVoiceTheme.colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
