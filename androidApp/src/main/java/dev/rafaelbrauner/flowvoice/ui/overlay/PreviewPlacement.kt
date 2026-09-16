package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.math.abs

// Faixa da tela em que a prévia pode ficar: do fim da barra de status/recorte ao topo do teclado (ou à base segura).
data class PreviewSpace(val top: Int, val bottom: Int)

enum class FocusSource { Caret, Field, Unknown }

// Com fromBottom, `edge` é a base do cartão e ele cresce para cima até maxHeight; sem, é o topo e ele cresce para baixo.
data class CardPlacement(
    val edge: Int,
    val fromBottom: Boolean,
    val maxHeight: Int,
    val compact: Boolean,
    val avoiding: FocusSource
)

data class CardSpan(val x: Int, val width: Int)

// Prévia longe do cursor (P139): o cartão abre na parte da faixa útil que não toca a linha do cursor (ou o campo, se
// ele for pequeno), o mais perto possível da bolha. O intervalo inteiro que o cartão pode ocupar fica fora da linha,
// então nem um cartão mais alto que o previsto a cobre. Sem posição conhecida, vale a metade da tela em que a bolha está.
object PreviewPlacement {
    private const val BIG_FIELD_FRACTION = 0.4f

    fun vertical(
        space: PreviewSpace,
        bubbleTop: Int,
        bubbleSize: Int,
        bubbleInLowerHalf: Boolean,
        caret: IntRange?,
        field: IntRange?,
        desiredHeight: Int,
        minHeight: Int,
        margin: Int
    ): CardPlacement? {
        val usable = space.bottom - space.top
        if (usable <= 0) return null
        val bubbleBottom = bubbleTop + bubbleSize
        // A linha seguinte também fica livre: com o cursor logo depois de uma quebra de linha, o caractere
        // anterior está uma linha acima dele.
        val caretBand = caret?.let { (it.first - margin)..(it.last + (it.last - it.first) + margin) }
        val fieldBand = field
            ?.takeIf { it.last - it.first <= usable * BIG_FIELD_FRACTION }
            ?.let { (it.first - margin)..(it.last + margin) }
        val (forbidden, source) = when {
            caretBand != null -> caretBand to FocusSource.Caret
            fieldBand != null -> fieldBand to FocusSource.Field
            else -> return byBubbleHalf(space, bubbleTop, bubbleBottom, bubbleInLowerHalf, desiredHeight, minHeight)
        }
        val bubbleCenter = bubbleTop + bubbleSize / 2
        val upperBottom = minOf(space.bottom, forbidden.first)
        val upperRoom = upperBottom - space.top
        val lowerTop = maxOf(space.top, forbidden.last)
        val lowerRoom = space.bottom - lowerTop
        val candidates = buildList {
            if (upperRoom > 0) {
                val height = minOf(desiredHeight, upperRoom)
                val bottom = bubbleBottom.coerceIn(space.top + height, upperBottom)
                add(
                    Candidate(
                        placement = CardPlacement(bottom, true, bottom - space.top, upperRoom < desiredHeight, source),
                        room = upperRoom,
                        distance = abs(bottom - height / 2 - bubbleCenter)
                    )
                )
            }
            if (lowerRoom > 0) {
                val height = minOf(desiredHeight, lowerRoom)
                val top = bubbleTop.coerceIn(lowerTop, space.bottom - height)
                add(
                    Candidate(
                        placement = CardPlacement(top, false, space.bottom - top, lowerRoom < desiredHeight, source),
                        room = lowerRoom,
                        distance = abs(top + height / 2 - bubbleCenter)
                    )
                )
            }
        }
        val chosen = candidates.filter { it.room >= desiredHeight }.minByOrNull { it.distance }
            ?: candidates.maxByOrNull { it.room }
            ?: return null
        return chosen.placement.takeIf { chosen.room >= minHeight }
    }

    fun horizontal(
        side: BubbleSide,
        bubbleX: Int,
        bubbleSize: Int,
        areaLeft: Int,
        areaRight: Int,
        gap: Int,
        maxWidth: Int
    ): CardSpan = if (side == BubbleSide.Right) {
        val width = minOf(maxWidth, bubbleX - gap - areaLeft).coerceAtLeast(0)
        CardSpan(x = bubbleX - gap - width, width = width)
    } else {
        val x = bubbleX + bubbleSize + gap
        CardSpan(x = x, width = minOf(maxWidth, areaRight - x).coerceAtLeast(0))
    }

    private fun byBubbleHalf(
        space: PreviewSpace,
        bubbleTop: Int,
        bubbleBottom: Int,
        bubbleInLowerHalf: Boolean,
        desiredHeight: Int,
        minHeight: Int
    ): CardPlacement? {
        val placement = if (bubbleInLowerHalf) {
            val edge = bubbleBottom.coerceIn(space.top, space.bottom)
            val room = edge - space.top
            CardPlacement(edge, true, room, room < desiredHeight, FocusSource.Unknown)
        } else {
            val edge = bubbleTop.coerceIn(space.top, space.bottom)
            val room = space.bottom - edge
            CardPlacement(edge, false, room, room < desiredHeight, FocusSource.Unknown)
        }
        return placement.takeIf { it.maxHeight >= minHeight }
    }

    private class Candidate(val placement: CardPlacement, val room: Int, val distance: Int)
}
