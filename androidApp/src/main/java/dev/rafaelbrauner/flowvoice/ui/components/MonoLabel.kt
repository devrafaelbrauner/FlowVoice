package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FlowVoiceTheme.colors.textTertiary,
    style: TextStyle = FlowVoiceTheme.typography.monoLabel,
    maxLines: Int = 1
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Preview
@Composable
private fun MonoLabelPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        MonoLabel("Últimas notas")
        MonoLabel("Transcrição concluída", color = FlowVoiceTheme.colors.accentText)
    }
}
