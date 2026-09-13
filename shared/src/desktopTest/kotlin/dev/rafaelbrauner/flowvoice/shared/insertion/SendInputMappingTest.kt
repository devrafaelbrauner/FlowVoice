package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertEquals

class SendInputMappingTest {
    @Test
    fun unicodeStrokesUseScanCodeAndUnicodeFlag() {
        val inputs = SendInputMapping.toKeyboardInputs(KeyStrokePlanner.plan("é"))

        assertEquals(
            listOf(
                KeyboardInputSpec(virtualKey = 0, scanCode = 0x00E9, flags = 0x0004),
                KeyboardInputSpec(virtualKey = 0, scanCode = 0x00E9, flags = 0x0006)
            ),
            inputs
        )
    }

    @Test
    fun surrogateCodeUnitsAreMappedIndividually() {
        val scanCodes = SendInputMapping.toKeyboardInputs(KeyStrokePlanner.plan("😀")).map { it.scanCode }

        assertEquals(listOf(0xD83D, 0xD83D, 0xDE00, 0xDE00), scanCodes)
    }

    @Test
    fun returnKeyUsesVirtualKeyWithoutUnicodeFlag() {
        val inputs = SendInputMapping.toKeyboardInputs(KeyStrokePlanner.plan("\n"))

        assertEquals(
            listOf(
                KeyboardInputSpec(virtualKey = 0x0D, scanCode = 0, flags = 0),
                KeyboardInputSpec(virtualKey = 0x0D, scanCode = 0, flags = 0x0002)
            ),
            inputs
        )
    }
}
