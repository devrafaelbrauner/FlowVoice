package dev.rafaelbrauner.flowvoice.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.rafaelbrauner.flowvoice.R

val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

@Immutable
data class FlowVoiceTypography(
    val loginTitle: TextStyle = sans(34f, FontWeight.SemiBold, lineHeight = 1.05f, tracking = -0.03f),
    val screenTitle: TextStyle = sans(30f, FontWeight.SemiBold, lineHeight = 1.05f, tracking = -0.03f),
    val screenTitleSecondary: TextStyle = sans(26f, FontWeight.SemiBold, lineHeight = 1.1f, tracking = -0.02f),
    val headline: TextStyle = sans(26f, FontWeight.SemiBold, lineHeight = 1.15f, tracking = -0.02f),
    val noteTitle: TextStyle = sans(22f, FontWeight.SemiBold, lineHeight = 1.2f),
    val itemTitle: TextStyle = sans(15f, FontWeight.SemiBold, lineHeight = 1.3f),
    val itemTitleSmall: TextStyle = sans(13f, FontWeight.SemiBold, lineHeight = 1.25f),
    val rowLabel: TextStyle = sans(14f, FontWeight.Medium, lineHeight = 1.3f),
    val body: TextStyle = sans(14f, FontWeight.Normal, lineHeight = 1.6f),
    val bodyMedium: TextStyle = sans(13f, FontWeight.Normal, lineHeight = 1.45f),
    val bodySmall: TextStyle = sans(12f, FontWeight.Normal, lineHeight = 1.35f),
    val button: TextStyle = sans(13f, FontWeight.SemiBold),
    val buttonSecondary: TextStyle = sans(13f, FontWeight.Medium),
    val buttonLarge: TextStyle = sans(15f, FontWeight.SemiBold),
    val stat: TextStyle = sans(20f, FontWeight.SemiBold, lineHeight = 1f),
    val tabLabel: TextStyle = sans(11f, FontWeight.Medium),
    val tabLabelActive: TextStyle = sans(11f, FontWeight.SemiBold),
    val monoLabel: TextStyle = mono(11f, FontWeight.Medium, lineHeight = 1f, tracking = 0.14f),
    val monoLabelSmall: TextStyle = mono(10f, FontWeight.Medium, lineHeight = 1.2f, tracking = 0.10f),
    val monoStatus: TextStyle = mono(11f, FontWeight.Medium, lineHeight = 1f),
    val monoRoute: TextStyle = mono(11f, FontWeight.Medium, lineHeight = 1.3f, tracking = 0.08f),
    val monoValue: TextStyle = mono(12f, FontWeight.Normal),
    val monoKey: TextStyle = mono(13f, FontWeight.Normal),
    val monoTimestamp: TextStyle = mono(11f, FontWeight.Normal),
    val log: TextStyle = mono(11f, FontWeight.Normal, lineHeight = 1.5f),
    val wordmark: TextStyle = mono(13f, FontWeight.Bold, lineHeight = 1f, tracking = 0.20f),
)

internal fun FlowVoiceTypography.toMaterialTypography(): Typography = Typography(
    displaySmall = loginTitle,
    headlineLarge = screenTitle,
    headlineMedium = screenTitleSecondary,
    headlineSmall = headline,
    titleLarge = noteTitle,
    titleMedium = itemTitle,
    titleSmall = itemTitleSmall,
    bodyLarge = body,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = button,
    labelMedium = monoValue,
    labelSmall = monoLabel,
)

private fun sans(size: Float, weight: FontWeight, lineHeight: Float? = null, tracking: Float = 0f) =
    style(InstrumentSans, size, weight, lineHeight, tracking)

private fun mono(size: Float, weight: FontWeight, lineHeight: Float? = null, tracking: Float = 0f) =
    style(JetBrainsMono, size, weight, lineHeight, tracking)

private fun style(family: FontFamily, size: Float, weight: FontWeight, lineHeight: Float?, tracking: Float) =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight?.em ?: TextUnit.Unspecified,
        letterSpacing = tracking.em,
    )
