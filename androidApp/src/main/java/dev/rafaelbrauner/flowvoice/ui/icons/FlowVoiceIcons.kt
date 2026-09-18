package dev.rafaelbrauner.flowvoice.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object FlowVoiceIcons {

    val Mic: ImageVector by lazy {
        strokeIcon(
            "Mic",
            "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z",
            "M5 11a7 7 0 0 0 14 0",
            "M12 18v3"
        )
    }

    val MicFilled: ImageVector by lazy {
        icon("MicFilled") {
            addPath(
                pathData = addPathNodes("M12 2.5a3 3 0 0 1 3 3v5.5a3 3 0 0 1-6 0V5.5a3 3 0 0 1 3-3z"),
                fill = SolidColor(Color.Black)
            )
            strokePaths(this, "M4.8 11.2a7.2 7.2 0 0 0 14.4 0", "M12 18.4v3.1")
        }
    }

    val Notes: ImageVector by lazy {
        strokeIcon("Notes", "M5 4h14v16H5z", "M8.5 9h7", "M8.5 13h7", "M8.5 16.5h4")
    }

    val Dictionary: ImageVector by lazy {
        strokeIcon(
            "Dictionary",
            "M4 5.5A2.5 2.5 0 0 1 6.5 3H19v15H6.5A2.5 2.5 0 0 0 4 20.5z",
            "M9 8.5l2.5 5 2.5-5",
            "M10 12h3"
        )
    }

    val Settings: ImageVector by lazy {
        strokeIcon(
            "Settings",
            "M4 8h11",
            "M19 8h1",
            "M4 16h5",
            "M13 16h7",
            "M19.2 8a2.2 2.2 0 1 1-4.4 0a2.2 2.2 0 1 1 4.4 0z",
            "M13.2 16a2.2 2.2 0 1 1-4.4 0a2.2 2.2 0 1 1 4.4 0z"
        )
    }

    val Diagnostics: ImageVector by lazy {
        strokeIcon("Diagnostics", "M3 12h4l2.5-6 5 12 2.5-6H21")
    }

    val ChevronDoubleLeft: ImageVector by lazy {
        strokeIcon("ChevronDoubleLeft", "M11.5 7l-5 5 5 5", "M17.5 7l-5 5 5 5")
    }

    val ChevronDoubleRight: ImageVector by lazy {
        strokeIcon("ChevronDoubleRight", "M6.5 7l5 5-5 5", "M12.5 7l5 5-5 5")
    }

    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight", "M9.5 6l6 6-6 6")
    }

    val Check: ImageVector by lazy {
        strokeIcon("Check", "M5 12.5l4.5 4.5L19 7.5")
    }

    val ArrowBack: ImageVector by lazy {
        strokeIcon("ArrowBack", "M19 12H5", "M11 6l-6 6 6 6")
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            addPath(
                pathData = addPathNodes("M8 7h8a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z"),
                fill = SolidColor(Color.Black)
            )
        }
    }

    private const val STROKE_WIDTH = 1.7f

    private fun strokeIcon(name: String, vararg paths: String): ImageVector =
        icon(name) { strokePaths(this, *paths) }

    private fun strokePaths(builder: ImageVector.Builder, vararg paths: String) {
        paths.forEach { path ->
            builder.addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
    }

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
}
