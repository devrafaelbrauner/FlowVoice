package dev.rafaelbrauner.flowvoice.ui.screens.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NotesRoute(initialNoteId: String?, modifier: Modifier = Modifier) {
    Box(modifier) { Text("Notas") }
}
