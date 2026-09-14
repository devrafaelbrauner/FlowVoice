package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun FvCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(FlowVoiceRadius.card),
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = FlowVoiceTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val highlighted = selected || (onClick != null && (pressed || hovered))
    val clickable = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accentSurface else colors.surface)
            .border(1.dp, if (highlighted) colors.accentBorder else colors.hairlineCard, shape)
            .then(clickable)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun FvDivider(
    modifier: Modifier = Modifier,
    color: Color = FlowVoiceTheme.colors.hairline
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Preview
@Composable
private fun FvCardPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        FvCard(onClick = {}) {
            Text("Reunião de orçamento", style = FlowVoiceTheme.typography.itemTitle, color = FlowVoiceTheme.colors.textPrimary)
        }
        FvCard(selected = true) {
            Text("Selecionado", style = FlowVoiceTheme.typography.itemTitleSmall, color = FlowVoiceTheme.colors.accentText)
        }
        FvDivider(Modifier.padding(vertical = 4.dp))
    }
}
