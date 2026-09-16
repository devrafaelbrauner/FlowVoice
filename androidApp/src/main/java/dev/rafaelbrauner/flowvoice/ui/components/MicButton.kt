package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.icons.FlowVoiceIcons
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun MicButton(
    // Nulo quando o contêiner trata toque e semântica (bolha arrastável, P138).
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    iconSize: Dp = 52.dp,
    pulsing: Boolean = true,
    contentDescription: String? = "Ditar"
) {
    val colors = FlowVoiceTheme.colors
    val progress = if (pulsing) {
        rememberInfiniteTransition(label = "micPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(MicPulse.DURATION_MS, easing = LinearEasing)),
            label = "micPulseProgress"
        ).value
    } else {
        0f
    }
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                if (!pulsing) return@drawBehind
                val ring = MicPulse.ring(progress)
                if (ring.alpha > 0f) {
                    drawCircle(
                        color = colors.accent.copy(alpha = ring.alpha),
                        radius = this.size.minDimension / 2f + ring.spread * MicPulse.MAX_SPREAD.toPx()
                    )
                }
            }
            .clip(CircleShape)
            .background(colors.accent)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = FlowVoiceIcons.MicFilled,
            contentDescription = contentDescription,
            tint = colors.onAccent,
            modifier = Modifier.size(iconSize)
        )
    }
}

internal object MicPulse {
    const val DURATION_MS = 2_400
    val MAX_SPREAD = 22.dp
    private const val EXPAND_FRACTION = 0.7f
    private const val START_ALPHA = 0.45f
    private val easeOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

    data class Ring(val spread: Float, val alpha: Float)

    fun ring(progress: Float): Ring {
        val t = progress.coerceIn(0f, 1f)
        return if (t <= EXPAND_FRACTION) {
            val eased = easeOut.transform(t / EXPAND_FRACTION)
            Ring(spread = eased, alpha = START_ALPHA * (1f - eased))
        } else {
            val eased = easeOut.transform((t - EXPAND_FRACTION) / (1f - EXPAND_FRACTION))
            Ring(spread = 1f - eased, alpha = 0f)
        }
    }
}

@Preview
@Composable
private fun MicButtonPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        Box(Modifier.padding(24.dp)) { MicButton(onClick = {}) }
        MicButton(onClick = {}, size = 40.dp, iconSize = 20.dp, pulsing = false)
    }
}
