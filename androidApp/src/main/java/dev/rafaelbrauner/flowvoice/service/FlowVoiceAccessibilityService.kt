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
import android.os.SystemClock
import android.text.Spanned
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import dev.rafaelbrauner.flowvoice.shared.insertion.CommitChunks
import dev.rafaelbrauner.flowvoice.shared.insertion.CursorInsertion
import dev.rafaelbrauner.flowvoice.shared.insertion.FocusedFieldDiagnostic
import dev.rafaelbrauner.flowvoice.shared.insertion.FocusedPackage
import dev.rafaelbrauner.flowvoice.shared.insertion.InsertionGuard
import dev.rafaelbrauner.flowvoice.shared.insertion.InsertionTarget

class FlowVoiceAccessibilityService : AccessibilityService(), FocusedFieldAccess {

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

    private val previousApps by lazy { PreviousAppTracker(packageName) { SystemClock.elapsedRealtime() } }
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

    override val ownPackage: String
        get() = packageName

    override fun currentInputGeneration(): Int? =
        inputGeneration.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

    // Só o tipo do input que o teclado recebeu (API 33+), sem ler o campo: é o que a leitura antes do
    // cursor precisa saber antes de ler qualquer coisa (Y1). Abaixo da 33 essa leitura não existe.
    override fun inputIsPassword(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val inputType = runCatching { inputMethod?.currentInputEditorInfo?.inputType }.getOrNull() ?: return false
        return InsertionGuard.isPasswordInputType(inputType)
    }

    fun previousAppLaunchIntent(): Intent? =
        previousApps.target?.let { packageManager.getLaunchIntentForPackage(it) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun noteExternalLaunch() = previousApps.noteExternalLaunch()

    private fun isActiveAppWindow(windowId: Int): Boolean {
        freshAccessibilityData()
        val windows = runCatching { windows }.getOrNull() ?: return false
        return try {
            val active = windows.firstOrNull { it.isActive } ?: return false
            active.id == windowId && active.type == AccessibilityWindowInfo.TYPE_APPLICATION
        } finally {
            releaseWindows(windows)
        }
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
        val windows = runCatching { windows }.getOrNull() ?: return null
        return try {
            val keyboard = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
            val bounds = Rect()
            keyboard.getBoundsInScreen(bounds)
            bounds.top.takeIf { bounds.height() > 0 }
        } finally {
            releaseWindows(windows)
        }
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

    override fun focusedPackage(): String? {
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

    override fun insertDirect(text: String, deleteBefore: Int, excludedPackage: String?): InsertResult {
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
            if (deleteBefore > 0) {
                // A AccessibilityInputConnection não expõe finishComposingText, mas commitText encerra
                // a composição por contrato, e um commit vazio a encerra sem escrever nada (P142). Sem
                // isso o deleteSurroundingText logo abaixo disputa com a composição do teclado — a
                // suspeita para o `drift=1` medido no S26 (2026-09-16 10:29 e 11:05), um caractere a
                // mais no campo do que o app achava ter escrito. O que a composição levar é o mesmo que
                // o commitText do texto levaria de qualquer jeito, e quem confere o resultado é o
                // DirectFieldWrite, que refaz o fim do campo pela rota atômica quando não bate.
                connection.commitText("", 1, null)
                // Apaga o ponto que o próprio FlowVoice inseriu ao fechar o trecho anterior (P144).
                // Roda depois das travas de destino e de senha, nunca antes.
                connection.deleteSurroundingText(deleteBefore, 0)
            }
            // Em pedaços curtos (CommitChunks): o corpo de nota do Samsung Notes trata um commit longo como
            // colagem, fora de ordem e com linha nova no fim. As travas acima valem para todos os pedaços.
            CommitChunks.split(text).forEach { connection.commitText(it, 1, null) }
        } catch (error: Throwable) {
            Log.e(TAG, "commitText falhou", error)
            return InsertResult(false, "commitText", "commitText falhou: ${error.message}")
        }

        Log.i(TAG, "commitText executado")
        return InsertResult(true, "commitText", "commitText(...) executado")
    }

    // Texto que está de fato logo antes do cursor (P148). `getSurroundingText` existe na
    // AccessibilityInputConnection (API 33+) e devolve o trecho junto com a posição da seleção dentro
    // dele; sem conexão, sem editor ou fora da API, quem chamou fica com a conta do próprio app. As travas
    // de destino e de senha ficam no GuardedFieldInserter, que só chama isto depois delas (Y1).
    override fun textBeforeCursor(limit: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (limit <= 0) return null
        val connection = inputMethod?.currentInputConnection ?: return null
        val surrounding = runCatching { connection.getSurroundingText(limit, 0, 0) }.getOrNull() ?: return null
        val text = surrounding.text?.toString() ?: return null
        val cursor = surrounding.selectionStart.coerceIn(0, text.length)
        return text.substring(0, cursor)
    }

    override fun insertFallback(text: String, deleteBefore: Int, excludedPackage: String?): InsertResult =
        setTextAtCursor(text, deleteBefore, excludedPackage, precondition = null)
            ?: InsertResult(false, "ACTION_SET_TEXT", "campo não conferido: nada inserido")

    // O reparo automático da P142 pela mesma rota, só quando reescrever o campo inteiro não pode estragar
    // nada (Y5, regra em AtomicRewrite). Fora disso, null: o pipeline anota o desvio e não mexe no campo.
    override fun rewriteTail(text: String, deleteBefore: Int, excludedPackage: String): InsertResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return setTextAtCursor(text, deleteBefore, excludedPackage) { node, current ->
            val spans = (current as? Spanned)?.let { it.getSpans(0, it.length, Any::class.java).isNotEmpty() } ?: false
            val skip = AtomicRewrite.skip(
                className = node.className,
                nodeText = current,
                nodeHasSpans = spans,
                nodeSelectionStart = node.textSelectionStart,
                nodeSelectionEnd = node.textSelectionEnd,
                field = current?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
                    ?.let { wholeFieldFromInput(it.length) }
            )
            if (skip != null) Log.i(TAG, "rewrite_skipped motivo=${skip.reason} classe=${node.className}")
            skip == null
        }
    }

    // O campo como o teclado o vê, pedido com folga dos dois lados do cursor: se o nó mostra o campo
    // inteiro, isto começa na posição 0 e é igual ao texto do nó.
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun wholeFieldFromInput(nodeLength: Int): AtomicRewrite.Field? {
        val connection = inputMethod?.currentInputConnection ?: return null
        val span = nodeLength + 1
        val surrounding = runCatching { connection.getSurroundingText(span, span, 0) }.getOrNull() ?: return null
        val text = surrounding.text?.toString() ?: return null
        return AtomicRewrite.Field(text, surrounding.offset, surrounding.selectionStart, surrounding.selectionEnd)
    }

    // ACTION_SET_TEXT no ponto do cursor. O `precondition` roda depois das travas de app e de senha e antes
    // de escrever; recusado, devolve null sem tocar no campo.
    private fun setTextAtCursor(
        text: String,
        deleteBefore: Int,
        excludedPackage: String?,
        precondition: ((AccessibilityNodeInfo, CharSequence?) -> Boolean)?
    ): InsertResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return InsertResult(false, "ACTION_SET_TEXT", "requer Android 8+ para inserir sem apagar o texto do campo")
        }

        freshAccessibilityData()
        val root = runCatching { rootInActiveWindow }.getOrNull()
            ?: return InsertResult(false, "ACTION_SET_TEXT", "nenhum foco de edição encontrado")
        val node = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        if (node == null) {
            releaseNode(root)
            return InsertResult(false, "ACTION_SET_TEXT", "nenhum foco de edição encontrado")
        }

        try {
            if (excludedPackage != null && InsertionTarget.isOwnApp(node.packageName, excludedPackage)) {
                return InsertResult(false, "ACTION_SET_TEXT", InsertionTarget.OWN_APP_MESSAGE, blocked = true)
            }
            // `isPassword` sozinho não pega "mostrar senha" nem VISIBLE_PASSWORD (P86): o tipo do input
            // também conta, e nos dois casos o texto do campo nem chega a ser lido.
            if (node.isPassword || InsertionGuard.isPasswordInputType(node.inputType)) {
                return InsertResult(false, "ACTION_SET_TEXT", InsertionGuard.PASSWORD_MESSAGE, blocked = true)
            }
            val current = node.text
            if (current == null && !node.isShowingHintText) {
                return InsertResult(false, "ACTION_SET_TEXT", "texto do campo ilegível: nada inserido para não apagar conteúdo")
            }
            if (precondition != null && !precondition(node, current)) return null
            val plan = CursorInsertion.plan(
                current = current?.toString(),
                showingHint = node.isShowingHintText,
                selectionStart = node.textSelectionStart,
                selectionEnd = node.textSelectionEnd,
                insert = text,
                deleteBefore = deleteBefore
            )
            if (!node.replaceText(plan.text)) error("performAction(ACTION_SET_TEXT) retornou false")
            val selectionArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, plan.cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, plan.cursor)
            }
            val cursorPlaced = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
            // O SET_TEXT deixa o cursor no fim. Sem reposicionar, inserir no meio do texto faria cada
            // trecho seguinte ir para o fim do campo (N2): desfaz a escrita e responde falha, e o trecho
            // fica pendente. Se nem desfazer der, o texto está no campo, e dizer falha o duplicaria.
            if (!cursorPlaced && plan.cursor != plan.text.length) {
                val original = if (node.isShowingHintText) "" else current?.toString().orEmpty()
                if (node.replaceText(original)) {
                    return InsertResult(false, "ACTION_SET_TEXT", "cursor não reposicionado: nada inserido")
                }
                return InsertResult(true, "ACTION_SET_TEXT", "texto inserido em ${node.className} (cursor não reposicionado)")
            }
            return InsertResult(true, "ACTION_SET_TEXT", "texto inserido no cursor em ${node.className}")
        } catch (error: Throwable) {
            return InsertResult(false, "ACTION_SET_TEXT", error.message ?: "erro desconhecido")
        } finally {
            releaseNode(node)
            releaseNode(root)
        }
    }

    private fun AccessibilityNodeInfo.replaceText(value: CharSequence): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun diagnoseFocusedField(): InsertResult {
        freshAccessibilityData()
        val root = runCatching { rootInActiveWindow }.getOrNull()
            ?: return InsertResult(false, "diagnóstico", "nenhum foco de edição encontrado")
        val node = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        if (node == null) {
            releaseNode(root)
            return InsertResult(false, "diagnóstico", "nenhum foco de edição encontrado")
        }

        try {
            val password = node.isPassword || InsertionGuard.isPasswordInputType(node.inputType)
            return InsertResult(
                success = true,
                route = "diagnóstico",
                message = FocusedFieldDiagnostic.describe(
                    className = node.className,
                    editable = node.isEditable,
                    focused = node.isFocused,
                    password = password,
                    textLength = if (password) 0 else node.text?.length ?: 0
                ),
            )
        } catch (error: Throwable) {
            return InsertResult(false, "diagnóstico", error.message ?: "erro desconhecido")
        } finally {
            releaseNode(node)
            releaseNode(root)
        }
    }

    private fun releaseNode(node: AccessibilityNodeInfo) {
        @Suppress("DEPRECATION")
        node.recycle()
    }

    // Abaixo da API 33 as janelas vêm de um pool; da 33 em diante recycle não faz nada.
    private fun releaseWindows(windows: List<AccessibilityWindowInfo>) {
        @Suppress("DEPRECATION")
        windows.forEach { it.recycle() }
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