package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val shape = RoundedCornerShape(FlowVoiceRadius.cardSmall)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairlineCard, shape)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(value)
                if (unit != null) {
                    withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = colors.textMuted)) {
                        append(" $unit")
                    }
                }
            },
            style = typography.stat,
            color = colors.textPrimary,
            maxLines = 1
        )
        Spacer(Modifier.height(5.dp))
        MonoLabel(label, style = typography.monoLabelSmall, maxLines = 2)
    }
}

@Preview
@Composable
private fun StatCardPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("1,2", "Latência média", Modifier.weight(1f), unit = "s")
            StatCard("34", "Ditados hoje", Modifier.weight(1f))
            StatCard("—", "Gasto hoje", Modifier.weight(1f))
        }
    }
}
