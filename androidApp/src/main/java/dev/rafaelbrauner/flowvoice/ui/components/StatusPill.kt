package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun StatusPill(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val tone = if (active) colors.accent else colors.destructive
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .heightIn(min = FlowVoiceSpacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(colors.statusPillBackground)
                .border(1.dp, if (active) colors.statusPillBorder else tone.copy(alpha = 0.30f), shape)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(tone, RoundedCornerShape(2.dp))
            )
            Text(
                text = if (active) "ATIVO" else "INATIVO",
                style = FlowVoiceTheme.typography.monoStatus,
                color = tone
            )
        }
    }
}

@Preview
@Composable
private fun StatusPillPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        StatusPill(active = true, onClick = {})
        StatusPill(active = false, onClick = {})
    }
}
