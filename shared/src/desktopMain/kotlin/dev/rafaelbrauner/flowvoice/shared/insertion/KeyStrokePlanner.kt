package dev.rafaelbrauner.flowvoice.shared.insertion

sealed interface KeyStroke {
    val keyUp: Boolean

    data class Unicode(val codeUnit: Char, override val keyUp: Boolean) : KeyStroke

    data class VirtualKey(val code: Int, override val keyUp: Boolean) : KeyStroke
}

object KeyStrokePlanner {
    const val VK_RETURN = 0x0D

    fun plan(text: String): List<KeyStroke> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val strokes = ArrayList<KeyStroke>(normalized.length * 2)
        for (unit in normalized) {
            if (unit == '\n') {
                strokes += KeyStroke.VirtualKey(VK_RETURN, keyUp = false)
                strokes += KeyStroke.VirtualKey(VK_RETURN, keyUp = true)
            } else {
                strokes += KeyStroke.Unicode(unit, keyUp = false)
                strokes += KeyStroke.Unicode(unit, keyUp = true)
            }
        }
        return strokes
    }
}
