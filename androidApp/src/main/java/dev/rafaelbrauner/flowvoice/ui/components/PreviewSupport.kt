package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

class ThemePreviewParameter : PreviewParameterProvider<Boolean> {
    override val values: Sequence<Boolean> = sequenceOf(true, false)
}

@Composable
internal fun FvPreviewSurface(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    FlowVoiceTheme(darkTheme = dark) {
        Column(
            modifier = Modifier
                .background(FlowVoiceTheme.colors.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
