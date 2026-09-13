package dev.rafaelbrauner.flowvoice.shared.insertion

data class KeyboardInputSpec(
    val virtualKey: Int,
    val scanCode: Int,
    val flags: Int
)

object SendInputMapping {
    const val KEYEVENTF_KEYUP = 0x0002
    const val KEYEVENTF_UNICODE = 0x0004

    fun toKeyboardInputs(strokes: List<KeyStroke>): List<KeyboardInputSpec> = strokes.map { stroke ->
        val keyUpFlag = if (stroke.keyUp) KEYEVENTF_KEYUP else 0
        when (stroke) {
            is KeyStroke.Unicode -> KeyboardInputSpec(
                virtualKey = 0,
                scanCode = stroke.codeUnit.code,
                flags = KEYEVENTF_UNICODE or keyUpFlag
            )
            is KeyStroke.VirtualKey -> KeyboardInputSpec(
                virtualKey = stroke.code,
                scanCode = 0,
                flags = keyUpFlag
            )
        }
    }
}
