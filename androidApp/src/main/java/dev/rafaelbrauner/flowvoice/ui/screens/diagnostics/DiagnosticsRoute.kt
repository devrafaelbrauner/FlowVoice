package dev.rafaelbrauner.flowvoice.ui.screens.diagnostics

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DiagnosticsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) { Text("Diagnóstico") }
}
