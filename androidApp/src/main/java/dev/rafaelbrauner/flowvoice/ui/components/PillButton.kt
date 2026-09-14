package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.rafaelbrauner.flowvoice.ui.icons.FlowVoiceIcons
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

enum class PillButtonVariant { Primary, Outline, Destructive }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillButtonVariant = PillButtonVariant.Primary,
    height: Dp = 38.dp,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    horizontalPadding: Dp = 16.dp
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = enabled && (pressed || hovered)

    val container = when (variant) {
        PillButtonVariant.Primary ->
            if (active) colors.primaryButtonContainerPressed else colors.primaryButtonContainer
        PillButtonVariant.Outline -> colors.outlineButtonContainer
        PillButtonVariant.Destructive -> colors.destructive
    }
    val border = when (variant) {
        PillButtonVariant.Outline ->
            if (active) colors.destructive.copy(alpha = 0.6f) else colors.outlineButtonBorder
        else -> Color.Transparent
    }
    val content = when (variant) {
        PillButtonVariant.Primary -> colors.primaryButtonContent
        PillButtonVariant.Outline -> if (active) colors.destructiveText else colors.outlineButtonContent
        PillButtonVariant.Destructive -> colors.onAccent
    }
    val textStyle = if (variant == PillButtonVariant.Outline) typography.buttonSecondary else typography.button

    Row(
        modifier = modifier
            .heightIn(min = max(height, FlowVoiceSpacing.minTouchTarget))
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .drawBehind {
                val pillHeight = height.toPx().coerceAtMost(size.height)
                val top = (size.height - pillHeight) / 2f
                if (container.alpha > 0f) {
                    drawRoundRect(
                        color = container,
                        topLeft = Offset(0f, top),
                        size = Size(size.width, pillHeight),
                        cornerRadius = CornerRadius(pillHeight / 2f)
                    )
                }
                if (border.alpha > 0f) {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        color = border,
                        topLeft = Offset(stroke / 2f, top + stroke / 2f),
                        size = Size(size.width - stroke, pillHeight - stroke),
                        cornerRadius = CornerRadius((pillHeight - stroke) / 2f),
                        style = Stroke(stroke)
                    )
                }
            }
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = content,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview
@Composable
private fun PillButtonPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        PillButton("Inserir", onClick = {}, height = 34.dp)
        PillButton("Cancelar", onClick = {}, variant = PillButtonVariant.Outline, height = 34.dp)
        PillButton("Parar", onClick = {}, variant = PillButtonVariant.Destructive)
        PillButton(
            "Nova nota",
            onClick = {},
            icon = FlowVoiceIcons.MicFilled,
            height = 40.dp,
            modifier = Modifier.fillMaxWidth()
        )
        PillButton("Desabilitado", onClick = {}, enabled = false)
    }
}
