package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

private const val TOGGLE_ANIMATION_MS = 180
private val TrackWidth = 46.dp
private val TrackHeight = 27.dp
private val TrackPadding = 2.dp
private val KnobSize = 21.dp

@Composable
fun FvToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null
) {
    val colors = FlowVoiceTheme.colors
    val track by animateColorAsState(
        if (checked) colors.toggleOnTrack else colors.toggleOffTrack,
        tween(TOGGLE_ANIMATION_MS),
        label = "toggleTrack"
    )
    val knob by animateColorAsState(
        if (checked) colors.toggleOnKnob else colors.toggleOffKnob,
        tween(TOGGLE_ANIMATION_MS),
        label = "toggleKnob"
    )
    val knobOffset by animateDpAsState(
        if (checked) TrackWidth - TrackPadding * 2 - KnobSize else 0.dp,
        tween(TOGGLE_ANIMATION_MS),
        label = "toggleOffset"
    )
    Box(
        modifier = modifier
            .sizeIn(minWidth = TrackWidth, minHeight = FlowVoiceSpacing.minTouchTarget)
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(TrackWidth, TrackHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(track)
                .padding(TrackPadding),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .offset { IntOffset(knobOffset.roundToPx(), 0) }
                    .size(KnobSize)
                    .clip(CircleShape)
                    .background(knob)
            )
        }
    }
}

@Preview
@Composable
private fun FvTogglePreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        FvToggle(checked = true, onCheckedChange = {})
        FvToggle(checked = false, onCheckedChange = {})
    }
}
