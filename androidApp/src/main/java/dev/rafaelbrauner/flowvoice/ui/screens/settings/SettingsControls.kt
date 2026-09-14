package dev.rafaelbrauner.flowvoice.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
internal fun FvDarkField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    textStyle: TextStyle = FlowVoiceTheme.typography.monoKey,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    label: String? = placeholder
) {
    val colors = FlowVoiceTheme.colors
    val shape = RoundedCornerShape(FlowVoiceRadius.field)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = if (label != null) modifier.semantics { contentDescription = label } else modifier,
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = colors.chipContent),
        cursorBrush = SolidColor(colors.accent),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FlowVoiceSpacing.minTouchTarget)
                    .clip(shape)
                    .background(colors.field)
                    .border(1.dp, colors.hairlineInput, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                inner()
            }
        }
    )
}

@Composable
internal fun FvFieldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = FlowVoiceTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val active = enabled && (pressed || hovered)
    val shape = RoundedCornerShape(FlowVoiceRadius.field)
    Box(
        modifier = modifier
            .heightIn(min = FlowVoiceSpacing.minTouchTarget)
            .clip(shape)
            .border(1.dp, if (active) colors.accentBorder else colors.outlineButtonBorder, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = FlowVoiceTheme.typography.buttonSecondary,
            color = when {
                !enabled -> colors.textTertiary
                active -> colors.accentText
                else -> colors.outlineButtonContent
            }
        )
    }
}

@Composable
internal fun FvTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = FlowVoiceTheme.colors.iconMuted,
    activeColor: Color = FlowVoiceTheme.colors.accentText
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .sizeIn(minWidth = FlowVoiceSpacing.minTouchTarget, minHeight = FlowVoiceSpacing.minTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = FlowVoiceTheme.typography.monoValue.copy(fontWeight = FontWeight.Medium),
            color = if (pressed || hovered) activeColor else color
        )
    }
}
