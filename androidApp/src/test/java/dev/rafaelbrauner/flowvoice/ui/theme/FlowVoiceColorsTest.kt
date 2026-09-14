package dev.rafaelbrauner.flowvoice.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowVoiceColorsTest {
    private val dark = darkFlowVoiceColors()
    private val light = lightFlowVoiceColors()

    @Test
    fun lightThemeUsesBurntOrangeForAccentText() {
        assertEquals(Color(0xFFB34700), light.accentText)
        assertEquals(FlowVoiceOrangeOnLight, light.accentText)
    }

    @Test
    fun darkThemeUsesBrandOrangeForAccentText() {
        assertEquals(FlowVoiceOrange, dark.accentText)
    }

    @Test
    fun brandOrangeIsTooFaintAsTextOnPaper() {
        assertTrue(contrast(FlowVoiceOrange, light.background) < 4.5)
    }

    @Test
    fun readableTextPairsMeetWcagAa() {
        listOf(dark, light).forEach { colors ->
            assertTrue(contrast(colors.textPrimary, colors.background) >= 4.5, "texto primário (dark=${colors.isDark})")
            assertTrue(contrast(colors.accentText, colors.background) >= 4.5, "acento como texto (dark=${colors.isDark})")
            assertTrue(
                contrast(colors.primaryButtonContent, colors.primaryButtonContainer) >= 4.5,
                "botão primário (dark=${colors.isDark})"
            )
            assertTrue(contrast(colors.heroTitle, colors.heroBottom) >= 4.5, "título do herói (dark=${colors.isDark})")
        }
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val a = foreground.luminance() + 0.05
        val b = background.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }
}
