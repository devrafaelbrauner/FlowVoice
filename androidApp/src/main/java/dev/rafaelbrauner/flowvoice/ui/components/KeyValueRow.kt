package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val style = FlowVoiceTheme.typography.monoValue
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(key, style = style, color = colors.textTertiary)
        Text(
            text = value,
            style = style,
            color = colors.keyValue,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview
@Composable
private fun KeyValueRowPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        KeyValueRow("aparelho", "SM_S948B")
        KeyValueRow("último alvo", "com.google.android.apps.messaging")
    }
}
