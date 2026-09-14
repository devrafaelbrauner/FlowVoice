package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingRoute(onDone: () -> Unit, onOpenKeySettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) { Text("Onboarding") }
}
