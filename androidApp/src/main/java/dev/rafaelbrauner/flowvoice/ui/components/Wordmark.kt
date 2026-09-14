package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val colors = FlowVoiceTheme.colors
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.textPrimary)) { append("FLOW") }
            withStyle(SpanStyle(color = colors.accentText)) { append("VOICE") }
        },
        style = FlowVoiceTheme.typography.wordmark,
        modifier = modifier
    )
}

@Preview
@Composable
private fun WordmarkPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) { Wordmark() }
}
