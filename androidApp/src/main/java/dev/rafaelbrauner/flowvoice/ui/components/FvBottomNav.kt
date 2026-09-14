package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.icons.FlowVoiceIcons
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

enum class FvTab(val label: String) {
    Home("Início"),
    Notes("Notas"),
    Dictionary("Dicionário"),
    Settings("Ajustes");

    val icon: ImageVector
        get() = when (this) {
            Home -> FlowVoiceIcons.Mic
            Notes -> FlowVoiceIcons.Notes
            Dictionary -> FlowVoiceIcons.Dictionary
            Settings -> FlowVoiceIcons.Settings
        }
}

@Composable
fun FvBottomNav(
    selected: FvTab,
    onSelect: (FvTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.chrome)
    ) {
        FvDivider()
        Row(Modifier.fillMaxWidth()) {
            FvTab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) colors.accentText else colors.textTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = active,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab) }
                        )
                        .drawBehind {
                            if (active) drawRect(tint, size = Size(size.width, 2.dp.toPx()))
                        }
                        .padding(top = 12.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                    Text(
                        text = tab.label,
                        style = if (active) typography.tabLabelActive else typography.tabLabel,
                        color = tint
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun FvBottomNavPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        FvBottomNav(selected = FvTab.Home, onSelect = {})
        FvBottomNav(selected = FvTab.Settings, onSelect = {})
    }
}
