package dev.rafaelbrauner.flowvoice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalFlowVoiceColors = staticCompositionLocalOf { darkFlowVoiceColors() }
private val LocalFlowVoiceTypography = staticCompositionLocalOf { FlowVoiceTypography() }

object FlowVoiceTheme {
    val colors: FlowVoiceColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFlowVoiceColors.current

    val typography: FlowVoiceTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalFlowVoiceTypography.current
}

@Composable
fun FlowVoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = remember(darkTheme) { if (darkTheme) darkFlowVoiceColors() else lightFlowVoiceColors() }
    val typography = remember { FlowVoiceTypography() }
    CompositionLocalProvider(
        LocalFlowVoiceColors provides colors,
        LocalFlowVoiceTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = typography.toMaterialTypography(),
            content = content
        )
    }
}

internal fun FlowVoiceColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primaryButtonContainer,
        onPrimary = primaryButtonContent,
        secondary = accentText,
        onSecondary = background,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        outline = textTertiary,
        outlineVariant = hairlineInput,
        error = destructive,
        onError = onAccent,
    )
}
