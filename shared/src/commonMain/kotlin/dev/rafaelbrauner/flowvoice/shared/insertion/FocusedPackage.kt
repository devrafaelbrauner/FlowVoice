package dev.rafaelbrauner.flowvoice.shared.insertion

// O EditorInfo do InputMethod da acessibilidade só é zerado quando o input termina; se o foco
// vai para uma janela sem campo, ele continua com o pacote do último editor. A janela ativa
// prevalece, e o editor só vale quando ela é desconhecida e o input está de fato iniciado.
object FocusedPackage {
    fun resolve(editorPackage: String?, inputStarted: Boolean, activeWindowPackage: String?): String? =
        activeWindowPackage ?: editorPackage.takeIf { inputStarted }
}
