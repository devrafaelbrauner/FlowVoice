package dev.rafaelbrauner.flowvoice.service

// App para onde o microfone do Início volta (P130). Usa o pacote da janela ativa, e não o do
// evento: a bolha de outro app dispara o evento sem tirar o foco do app de baixo. O próprio
// FlowVoice, o launcher (inclusive os recentes) e pacotes sem ícone nunca viram destino.
object PreviousApp {
    fun next(
        current: String?,
        activeWindowPackage: String?,
        ownPackage: String,
        homePackages: Set<String>,
        isLaunchable: (String) -> Boolean
    ): String? {
        val candidate = activeWindowPackage?.takeIf { it.isNotBlank() } ?: return current
        if (candidate == current || candidate == ownPackage || candidate in homePackages) return current
        return if (isLaunchable(candidate)) candidate else current
    }
}
