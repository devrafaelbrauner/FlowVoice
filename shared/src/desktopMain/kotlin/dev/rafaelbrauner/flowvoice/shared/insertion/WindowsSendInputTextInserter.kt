package dev.rafaelbrauner.flowvoice.shared.insertion

import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

class WindowsSendInputTextInserter(
    private val sendInputs: (List<KeyboardInputSpec>) -> Int = ::sendKeyboardInputs
) : TextInserter {
    override val isAvailable: Boolean = true

    override fun insert(text: String): TextInsertionResult {
        if (text.isEmpty()) return TextInsertionResult(false, ROUTE, "texto vazio")
        val inputs = SendInputMapping.toKeyboardInputs(KeyStrokePlanner.plan(text))
        val accepted = try {
            sendInputs(inputs)
        } catch (error: LinkageError) {
            return TextInsertionResult(false, ROUTE, "SendInput indisponível (${error::class.simpleName})")
        } catch (error: RuntimeException) {
            return TextInsertionResult(false, ROUTE, "SendInput falhou (${error::class.simpleName})")
        }
        return if (accepted == inputs.size) {
            TextInsertionResult(true, ROUTE, "${text.length} caracteres inseridos (${inputs.size} eventos)")
        } else {
            TextInsertionResult(
                false,
                ROUTE,
                "SendInput aceitou $accepted de ${inputs.size} eventos; o app ativo pode estar elevado (UIPI) ou bloqueando entrada"
            )
        }
    }

    companion object {
        const val ROUTE = "SendInput"
    }
}

class UnsupportedTextInserter(private val systemName: String) : TextInserter {
    override val isAvailable: Boolean = false

    override fun insert(text: String): TextInsertionResult = TextInsertionResult(
        false,
        ROUTE,
        "inserção direta não implementada em $systemName; ${text.length} caracteres não inseridos"
    )

    companion object {
        const val ROUTE = "sem-inserção"
    }
}

private fun sendKeyboardInputs(specs: List<KeyboardInputSpec>): Int {
    if (specs.isEmpty()) return 0
    @Suppress("UNCHECKED_CAST")
    val inputs = WinUser.INPUT().toArray(specs.size) as Array<WinUser.INPUT>
    specs.forEachIndexed { index, spec ->
        inputs[index].apply {
            type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
            input.setType("ki")
            input.ki.wVk = WinDef.WORD(spec.virtualKey.toLong())
            input.ki.wScan = WinDef.WORD(spec.scanCode.toLong())
            input.ki.dwFlags = WinDef.DWORD(spec.flags.toLong())
            input.ki.time = WinDef.DWORD(0)
            input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(0)
        }
    }
    return User32.INSTANCE.SendInput(WinDef.DWORD(inputs.size.toLong()), inputs, inputs[0].size()).toInt()
}
