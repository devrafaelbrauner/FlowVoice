package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun ProvisionalText(
    finalized: String,
    provisional: String,
    modifier: Modifier = Modifier,
    style: TextStyle = FlowVoiceTheme.typography.body,
    finalizedColor: Color = FlowVoiceTheme.colors.textPrimary,
    provisionalColor: Color = FlowVoiceTheme.colors.provisional,
    maxLines: Int = Int.MAX_VALUE,
    underlineOffset: Dp = 3.5.dp
) {
    val joined = remember(finalized, provisional) { ProvisionalUnderline.join(finalized, provisional) }
    val annotated = remember(joined, finalizedColor, provisionalColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = finalizedColor)) {
                append(joined.text.substring(0, joined.provisionalStart))
            }
            withStyle(SpanStyle(color = provisionalColor)) {
                append(joined.text.substring(joined.provisionalStart))
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            val lines = (0 until result.lineCount).map { result.getLineStart(it) to result.getLineEnd(it) }
            val thickness = ProvisionalUnderline.THICKNESS.toPx()
            val dotted = PathEffect.dashPathEffect(floatArrayOf(thickness, thickness))
            ProvisionalUnderline.segments(annotated.text, lines, joined.provisionalStart, annotated.length)
                .forEach { (start, end) ->
                    val line = result.getLineForOffset(start)
                    val y = result.getLineBaseline(line) + underlineOffset.toPx() + thickness / 2f
                    drawLine(
                        color = provisionalColor,
                        start = Offset(result.getBoundingBox(start).left, y),
                        end = Offset(result.getBoundingBox(end - 1).right, y),
                        strokeWidth = thickness,
                        pathEffect = dotted
                    )
                }
        }
    )
}

internal object ProvisionalUnderline {
    val THICKNESS = 2.dp

    data class Joined(val text: String, val provisionalStart: Int)

    fun join(finalized: String, provisional: String): Joined {
        val needsSpace = finalized.isNotEmpty() &&
            provisional.isNotEmpty() &&
            !finalized.last().isWhitespace() &&
            !provisional.first().isWhitespace()
        val head = if (needsSpace) "$finalized " else finalized
        return Joined(head + provisional, head.length)
    }

    fun segments(
        text: CharSequence,
        lines: List<Pair<Int, Int>>,
        start: Int,
        end: Int
    ): List<Pair<Int, Int>> {
        if (start >= end) return emptyList()
        return lines.mapNotNull { (lineStart, lineEnd) ->
            var segmentStart = maxOf(start, lineStart)
            var segmentEnd = minOf(end, lineEnd)
            while (segmentStart < segmentEnd && text[segmentStart].isWhitespace()) segmentStart++
            while (segmentEnd > segmentStart && text[segmentEnd - 1].isWhitespace()) segmentEnd--
            if (segmentStart < segmentEnd) segmentStart to segmentEnd else null
        }
    }
}

@Preview
@Composable
private fun ProvisionalTextPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FvPreviewSurface(dark) {
        ProvisionalText(
            finalized = "Fechamos o escopo da fase 2 com a Marina.",
            provisional = "precisa confirmar com o financeiro na sexta"
        )
    }
}
