package dev.rafaelbrauner.flowvoice.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.rafaelbrauner.flowvoice.shared.insertion.CursorInsertion
import dev.rafaelbrauner.flowvoice.shared.insertion.InsertionTarget

class FlowVoiceAccessibilityService : AccessibilityService() {

    data class InsertResult(
        val success: Boolean,
        val route: String,
        val message: String,
        val blocked: Boolean = false,
    ) {
        val summary: String
            get() = if (success) "[$route] $message" else "[$route] FALHOU — $message"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val current = serviceInfo ?: AccessibilityServiceInfo()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            current.flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
        } else {
            current.flags
        }
        serviceInfo = current.apply { this.flags = flags }
        Log.i(TAG, "Serviço de acessibilidade conectado (flags=0x${flags.toString(16)})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (
            intent?.getStringExtra("fv_poc_debug_source") == "adb" &&
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        ) {
            val action = intent.getStringExtra("fv_poc_action") ?: "diagnose"
            val text = intent.getStringExtra("fv_poc_text") ?: "POC FlowVoice — "
            val result = when (action) {
                "direct" -> insertDirect(text)
                "fallback" -> insertFallback(text)
                "diagnose" -> diagnoseFocusedField()
                else -> InsertResult(false, "debug", "ação desconhecida: $action")
            }
            Log.i(TAG, "[POC_ADB] action=$action resultado=${result.summary}")
        }
        return START_NOT_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        clearInstanceIfCurrent()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearInstanceIfCurrent()
        super.onDestroy()
    }

    fun insertDirect(text: String, excludedPackage: String? = null): InsertResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return InsertResult(false, "commitText", "requer Android 13+ (API 33)")
        }

        val inputMethod = inputMethod
            ?: return InsertResult(false, "commitText", "getInputMethod() nulo")
        val connection = inputMethod.currentInputConnection
            ?: return InsertResult(false, "commitText", "currentInputConnection nulo")

        val packageName = runCatching {
            inputMethod.currentInputEditorInfo?.packageName
        }.getOrDefault(null)
        if (excludedPackage != null && InsertionTarget.isOwnApp(packageName, excludedPackage)) {
            return InsertResult(false, "commitText", InsertionTarget.OWN_APP_MESSAGE, blocked = true)
        }

        try {
            connection.commitText(text, 1, null)
        } catch (error: Throwable) {
            Log.e(TAG, "commitText falhou", error)
            return InsertResult(false, "commitText", "commitText falhou: ${error.message}")
        }

        Log.i(TAG, "commitText executado no pacote=$packageName")
        return InsertResult(true, "commitText", "commitText(...) executado")
    }

    fun insertFallback(text: String, excludedPackage: String? = null): InsertResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return InsertResult(false, "ACTION_SET_TEXT", "requer Android 8+ para inserir sem apagar o texto do campo")
        }

        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return InsertResult(false, "ACTION_SET_TEXT", "nenhum foco de edição encontrado")

        try {
            if (excludedPackage != null && InsertionTarget.isOwnApp(node.packageName, excludedPackage)) {
                return InsertResult(false, "ACTION_SET_TEXT", InsertionTarget.OWN_APP_MESSAGE, blocked = true)
            }
            if (node.isPassword) {
                return InsertResult(false, "ACTION_SET_TEXT", "campo de senha: nada inserido")
            }
            val current = node.text
            if (current == null && !node.isShowingHintText) {
                return InsertResult(false, "ACTION_SET_TEXT", "texto do campo ilegível: nada inserido para não apagar conteúdo")
            }
            val plan = CursorInsertion.plan(
                current = current?.toString(),
                showingHint = node.isShowingHintText,
                selectionStart = node.textSelectionStart,
                selectionEnd = node.textSelectionEnd,
                insert = text
            )
            val textArgs = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, plan.text)
            }
            val performed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textArgs)
            if (!performed) error("performAction(ACTION_SET_TEXT) retornou false")
            val selectionArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, plan.cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, plan.cursor)
            }
            val cursorPlaced = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
            return InsertResult(
                success = true,
                route = "ACTION_SET_TEXT",
                message = if (cursorPlaced) {
                    "texto inserido no cursor em ${node.className}"
                } else {
                    "texto inserido no cursor em ${node.className} (cursor não reposicionado)"
                },
            )
        } catch (error: Throwable) {
            return InsertResult(false, "ACTION_SET_TEXT", error.message ?: "erro desconhecido")
        } finally {
            releaseNode(node)
        }
    }

    fun diagnoseFocusedField(): InsertResult {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return InsertResult(false, "diagnóstico", "nenhum foco de edição encontrado")

        try {
            val preview = node.text?.take(40)
            return InsertResult(
                success = true,
                route = "diagnóstico",
                message = "classe=${node.className}, editável=${node.isEditable}, focado=${node.isFocused}, texto='${preview?.ifBlank { "(vazio)" }}'",
            )
        } catch (error: Throwable) {
            return InsertResult(false, "diagnóstico", error.message ?: "erro desconhecido")
        } finally {
            releaseNode(node)
        }
    }

    private fun releaseNode(node: AccessibilityNodeInfo) {
        @Suppress("DEPRECATION")
        node.recycle()
    }

    private fun clearInstanceIfCurrent() {
        if (instance === this) {
            instance = null
            Log.i(TAG, "Serviço de acessibilidade desconectado")
        }
    }

    companion object {
        const val TAG = "FlowVoiceA11y"
        private var instance: FlowVoiceAccessibilityService? = null

        val isRunning: Boolean
            get() = instance != null

        val service: FlowVoiceAccessibilityService?
            get() = instance
    }
}