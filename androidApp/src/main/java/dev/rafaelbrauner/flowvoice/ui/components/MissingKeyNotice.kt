package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

// Aviso que fica na tela até a transcrição estar pronta (chave ou modelo no aparelho) ou o usuário dispensar: o ditado sem chave falha
// antes de começar, e um toast some antes de dar tempo de agir (R5c).
@Composable
fun MissingKeyNotice(
    onConfigureKey: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    FvCard(modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            text = "Falta configurar a transcrição",
            style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.destructiveText
        )
        Text(
            text = "O ditado precisa da chave OpenRouter ou do modelo no aparelho. Configure um dos dois e tente de novo.",
            style = typography.bodySmall,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton(text = "Configurar", onClick = onConfigureKey, height = 36.dp)
            PillButton(text = "Agora não", onClick = onDismiss, variant = PillButtonVariant.Outline, height = 36.dp)
        }
    }
}

@Preview
@Composable
private fun MissingKeyNoticePreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        MissingKeyNotice(onConfigureKey = {}, onDismiss = {})
    }
}
