package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

const val LOG_BLOCK_MAX_LINES = 80

@Composable
fun LogBlock(
    lines: List<String>,
    modifier: Modifier = Modifier,
    maxLines: Int = LOG_BLOCK_MAX_LINES
) {
    val colors = FlowVoiceTheme.colors
    val shape = RoundedCornerShape(FlowVoiceRadius.cardSmall)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.field)
            .border(1.dp, colors.hairline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.take(maxLines).forEach { line ->
            Text(line, style = FlowVoiceTheme.typography.log, color = colors.textSecondary)
        }
    }
}

@Preview
@Composable
private fun LogBlockPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        LogBlock(
            listOf(
                "[09:41:12] [commitText] commitText(...) executado",
                "[09:41:10] Estado da sessão: finalizada",
                "[09:41:02] Serviço de acessibilidade conectado (flags=0x8001)"
            )
        )
    }
}
