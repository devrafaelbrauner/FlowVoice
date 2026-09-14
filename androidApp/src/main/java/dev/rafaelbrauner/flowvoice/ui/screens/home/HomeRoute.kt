package dev.rafaelbrauner.flowvoice.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeRoute(
    onOpenNotes: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) { Text("Início") }
}
