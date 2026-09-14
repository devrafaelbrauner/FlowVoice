package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun Waveform(
    modifier: Modifier = Modifier,
    bars: Int = 5,
    barWidth: Dp = 3.dp,
    height: Dp = 22.dp,
    gap: Dp = 2.5.dp,
    color: Color = FlowVoiceTheme.colors.accent,
    animating: Boolean = true,
    delayStepMs: Int = WaveformMotion.DEFAULT_DELAY_STEP_MS
) {
    Row(
        modifier = modifier.height(height),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(bars) { index ->
            val scale = if (animating) {
                rememberInfiniteTransition(label = "waveform$index").animateFloat(
                    initialValue = WaveformMotion.MIN_SCALE,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(WaveformMotion.DURATION_MS / 2, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * delayStepMs, StartOffsetType.FastForward)
                    ),
                    label = "waveformBar$index"
                ).value
            } else {
                WaveformMotion.IDLE_SCALE
            }
            Box(
                Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .graphicsLayer { scaleY = scale }
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

internal object WaveformMotion {
    const val DURATION_MS = 620
    const val DEFAULT_DELAY_STEP_MS = 90
    const val MIN_SCALE = 0.25f
    const val IDLE_SCALE = 0.45f
}

@Preview
@Composable
private fun WaveformPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        Waveform()
        Waveform(bars = 4, height = 20.dp, delayStepMs = 120)
        Waveform(bars = 3, animating = false)
    }
}
