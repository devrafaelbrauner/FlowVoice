package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.math.roundToInt

enum class BubbleSide { Left, Right }

enum class BubbleMove { Up, Down, OtherSide }

// Posição salva da bolha (P138): lado e fração da faixa vertical em que ela cabe. Sobrevive a rotação
// e troca de resolução, ao contrário de pixels.
data class BubblePosition(val side: BubbleSide, val fraction: Float) {
    val encodedSide: String
        get() = if (side == BubbleSide.Left) LEFT else RIGHT

    companion object {
        private const val LEFT = "left"
        private const val RIGHT = "right"

        val Default = BubblePosition(BubbleSide.Right, 0.3f)

        fun decode(side: String?, fraction: Float): BubblePosition {
            val decoded = when (side) {
                LEFT -> BubbleSide.Left
                RIGHT -> BubbleSide.Right
                else -> return Default
            }
            if (fraction.isNaN()) return Default
            return BubblePosition(decoded, fraction.coerceIn(0f, 1f))
        }
    }
}

// Área da tela em que a bolha pode ficar, em pixels da tela, já sem barras do sistema, recorte e margem.
data class SafeArea(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class BubblePoint(val x: Int, val y: Int)

// Janela da prévia colada à bolha (P139). Com fromBottom, y conta a partir da base da tela.
data class PreviewWindow(val x: Int, val y: Int, val width: Int, val fromBottom: Boolean)

object BubblePlacement {
    private const val MOVE_STEP = 0.1f
    private const val LOWER_HALF = 0.5f

    fun pointOf(position: BubblePosition, area: SafeArea, size: Int): BubblePoint {
        val x = if (position.side == BubbleSide.Left) area.left else maxX(area, size)
        val y = area.top + (position.fraction.coerceIn(0f, 1f) * travel(area, size)).roundToInt()
        return BubblePoint(x, y)
    }

    fun dragged(start: BubblePoint, dx: Float, dy: Float, area: SafeArea, size: Int): BubblePoint =
        BubblePoint(
            x = (start.x + dx.roundToInt()).coerceIn(area.left, maxX(area, size)),
            y = (start.y + dy.roundToInt()).coerceIn(area.top, area.top + travel(area, size))
        )

    fun snap(point: BubblePoint, area: SafeArea, size: Int): BubblePosition {
        val side = if (point.x + size / 2f < (area.left + area.right) / 2f) BubbleSide.Left else BubbleSide.Right
        val travel = travel(area, size)
        val fraction = if (travel == 0) 0f else ((point.y - area.top).toFloat() / travel).coerceIn(0f, 1f)
        return BubblePosition(side, fraction)
    }

    fun moved(position: BubblePosition, move: BubbleMove): BubblePosition = when (move) {
        BubbleMove.Up -> position.copy(fraction = (position.fraction - MOVE_STEP).coerceIn(0f, 1f))
        BubbleMove.Down -> position.copy(fraction = (position.fraction + MOVE_STEP).coerceIn(0f, 1f))
        BubbleMove.OtherSide -> position.copy(
            side = if (position.side == BubbleSide.Left) BubbleSide.Right else BubbleSide.Left
        )
    }

    // A prévia se estende da bolha para o centro da tela e cresce para longe da borda mais próxima
    // (para baixo na metade de cima, para cima na de baixo), sem mudar o ponto da bolha.
    fun previewWindow(
        position: BubblePosition,
        area: SafeArea,
        size: Int,
        maxWidthPx: Int,
        screenHeight: Int
    ): PreviewWindow {
        val bubble = pointOf(position, area, size)
        val width = minOf(maxWidthPx, area.right - area.left).coerceAtLeast(size)
        val x = if (position.side == BubbleSide.Left) area.left else area.right - width
        val fromBottom = position.fraction > LOWER_HALF
        val y = if (fromBottom) screenHeight - (bubble.y + size) else bubble.y
        return PreviewWindow(x = x, y = y, width = width, fromBottom = fromBottom)
    }

    private fun maxX(area: SafeArea, size: Int): Int = maxOf(area.left, area.right - size)

    private fun travel(area: SafeArea, size: Int): Int = maxOf(0, area.bottom - size - area.top)
}
