package dev.rafaelbrauner.flowvoice.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsRoute(onOpenDiagnostics: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) { Text("Ajustes") }
}
