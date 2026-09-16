package dev.rafaelbrauner.flowvoice.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.InputMethod
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import dev.rafaelbrauner.flowvoice.shared.insertion.CursorInsertion
import dev.rafaelbrauner.flowvoice.shared.insertion.FocusedFieldDiagnostic
import dev.rafaelbrauner.flowvoice.shared.insertion.FocusedPackage
import dev.rafaelbrauner.flowvoice.shared.insertion.InsertionGuard
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
        val base = current.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            base or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
        } else {
            base
        }
        serviceInfo = current.apply { this.flags = flags }
        homePackages = resolveHomePackages()
        Log.i(TAG, "Serviço de acessibilidade conectado (flags=0x${flags.toString(16)})")
    }

    private val previousApps by lazy { PreviousAppTracker(packageName) }
    private var homePackages: Set<String> = emptySet()

    // Muda a cada input novo ou encerrado, nunca num restartInput do mesmo campo: a inserção sem toque
    // (P139) só continua no input em que a anterior entrou, então outra conversa ou outro campo pausa.
    @Volatile
    private var inputGeneration = 0

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateInputMethod(): InputMethod = object : InputMethod(this) {
        override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
            super.onStartInput(attribute, restarting)
            if (!restarting) noteInputChanged()
        }

        override fun onFinishInput() {
            super.onFinishInput()
            noteInputChanged()
        }
    }

    private fun noteInputChanged() {
        inputGeneration++
        Log.i(TAG, "input_generation $inputGeneration")
    }

    fun currentInputGeneration(): Int? =
        inputGeneration.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

    fun previousAppLaunchIntent(): Intent? =
        previousApps.target?.let { packageManager.getLaunchIntentForPackage(it) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun noteExternalLaunch() = previousApps.noteExternalLaunch()

    private fun isActiveAppWindow(windowId: Int): Boolean {
        freshAccessibilityData()
        val active = runCatching { windows }.getOrNull()?.firstOrNull { it.isActive } ?: return false
        return active.id == windowId && active.type == AccessibilityWindowInfo.TYPE_APPLICATION
    }

    private fun resolveHomePackages(): Set<String> = runCatching {
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        ).mapNotNull { it.activityInfo?.packageName }.toSet()
    }.getOrDefault(emptySet())

    // Sem tipos de evento assinados (SEG-5) o cache de janelas e nós nunca é invalidado;
    // limpar antes de cada leitura garante posição do teclado e texto do campo atuais.
    private fun freshAccessibilityData() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { clearCache() }
        }
    }

    fun inputMethodTopOnScreen(): Int? {
        freshAccessibilityData()
        val keyboard = runCatching { windows }.getOrNull()
            ?.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?: return null
        val bounds = Rect()
        keyboard.getBoundsInScreen(bounds)
        return bounds.top.takeIf { bounds.height() > 0 }
    }

    // Só coordenadas, na tela: faixa vertical do campo em foco e da linha do cursor, para a prévia não cobrir o que
    // se digita (P139). O texto do campo nunca é lido.
    data class FocusGeometry(val field: IntRange?, val caret: IntRange?)

    fun focusGeometry(): FocusGeometry? {
        freshAccessibilityData()
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val node = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        return try {
            node?.let {
                val bounds = Rect()
                it.getBoundsInScreen(bounds)
                FocusGeometry(field = (bounds.top..bounds.bottom).takeIf { bounds.height() > 0 }, caret = caretLine(it))
            }
        } catch (error: RuntimeException) {
            null
        } finally {
            node?.let(::releaseNode)
            releaseNode(root)
        }
    }

    // Diagnóstico da leitura acima para o receiver de depuração: classe, limites, chaves de dado extra e se a linha
    // do cursor saiu. Nada de texto nem de índice do cursor.
    internal fun describeFocusGeometry(): String {
        freshAccessibilityData()
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return "root=null"
        val node = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        return try {
            if (node == null) {
                "focus=null"
            } else {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val extraKeys = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) node.availableExtraData else emptyList()
                "focus=${node.className} editable=${node.isEditable} bounds=${bounds.toShortString()} " +
                    "extraData=$extraKeys selectionKnown=${node.textSelectionEnd > 0} caret=${caretLine(node)}"
            }
        } catch (error: RuntimeException) {
            "erro=${error.javaClass.simpleName}"
        } finally {
            node?.let(::releaseNode)
            releaseNode(root)
        }
    }

    // Retângulo do caractere antes do cursor (EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY). Sem suporte, sem caractere
    // antes ou sem resposta, nulo: vale o campo inteiro.
    private fun caretLine(node: AccessibilityNodeInfo): IntRange? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        if (key !in node.availableExtraData) return null
        val end = node.textSelectionEnd
        if (end <= 0) return null
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, end - 1)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 1)
        }
        if (!node.refreshWithExtraData(key, args)) return null
        val rect = BundleCompat.getParcelableArray(node.extras, key, RectF::class.java)
            ?.firstOrNull() as? RectF
            ?: return null
        return rect.top.toInt()..rect.bottom.toInt()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val windowId = event.windowId
        previousApps.onWindowStateChanged(
            packageName = event.packageName?.toString(),
            homePackages = homePackages,
            isActiveAppWindow = { isActiveAppWindow(windowId) },
            isLaunchable = { packageManager.getLaunchIntentForPackage(it) != null }
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        clearInstanceIfCurrent()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearInstanceIfCurrent()
        super.onDestroy()
    }

    fun focusedPackage(): String? {
        var editorPackage: String? = null
        var inputStarted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val method = inputMethod
                editorPackage = method?.currentInputEditorInfo?.packageName
                inputStarted = method?.currentInputStarted == true
            }
        }
        return FocusedPackage.resolve(editorPackage, inputStarted, activeWindowPackage())
    }

    private fun activeWindowPackage(): String? {
        freshAccessibilityData()
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            releaseNode(root)
        }
    }

    fun insertDirect(text: String, deleteBefore: Int = 0, excludedPackage: String? = null): InsertResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return InsertResult(false, "commitText", "requer Android 13+ (API 33)")
        }

        val inputMethod = inputMethod
            ?: return InsertResult(false, "commitText", "getInputMethod() nulo")
        val connection = inputMethod.currentInputConnection
            ?: return InsertResult(false, "commitText", "currentInputConnection nulo")

        val editorInfo = runCatching { inputMethod.currentInputEditorInfo }.getOrNull()
        if (excludedPackage != null && InsertionTarget.isOwnApp(editorInfo?.packageName, excludedPackage)) {
            return InsertResult(false, "commitText", InsertionTarget.OWN_APP_MESSAGE, blocked = true)
        }
        if (editorInfo != null && InsertionGuard.isPasswordInputType(editorInfo.inputType)) {
            return InsertResult(false, "commitText", InsertionGuard.PASSWORD_MESSAGE, blocked = true)
        }

        try {
            // Apaga o ponto que o próprio FlowVoice inseriu ao fechar o trecho anterior (P144). Roda
            // depois das travas de destino e de senha, nunca antes.
            if (deleteBefore > 0) connection.deleteSurroundingText(deleteBefore, 0)
            // Com palavra em composição no teclado, commitText a substitui e o texto sai truncado (P142).
            // A AccessibilityInputConnection não expõe finishComposingText, então não há como encerrá-la aqui.
            connection.commitText(text, 1, null)
        } catch (error: Throwable) {
            Log.e(TAG, "commitText falhou", error)
            return InsertResult(false, "commitText", "commitText falhou: ${error.message}")
        }

        Log.i(TAG, "commitText executado")
        return InsertResult(true, "commitText", "commitText(...) executado")
    }

    fun insertFallback(text: String, deleteBefore: Int = 0, excludedPackage: String? = null): InsertResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return InsertResult(false, "ACTION_SET_TEXT", "requer Android 8+ para inserir sem apagar o texto do campo")
        }

        freshAccessibilityData()
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
                insert = text,
                deleteBefore = deleteBefore
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
        freshAccessibilityData()
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return InsertResult(false, "diagnóstico", "nenhum foco de edição encontrado")

        try {
            return InsertResult(
                success = true,
                route = "diagnóstico",
                message = FocusedFieldDiagnostic.describe(
                    className = node.className,
                    editable = node.isEditable,
                    focused = node.isFocused,
                    password = node.isPassword,
                    textLength = node.text?.length ?: 0
                ),
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