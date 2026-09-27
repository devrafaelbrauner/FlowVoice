package dev.rafaelbrauner.flowvoice.service

// App para onde o microfone do Início volta (P130). Medido no S26: a janela de uma activity que
// abre chega no evento já como janela ativa do tipo aplicação; bolhas de outros apps, teclado,
// cortina e painéis do launcher não. Por isso o pacote do evento só conta quando a janela ativa é
// a dele (consulta ao system_server, sem tocar no processo do app), e só depois das saídas baratas.
// Apps que o próprio FlowVoice abriu (Ajustes, compartilhamento) não viram destino até o usuário
// passar pelo launcher.
//
// O destino vence (Y3): vale por TARGET_MAX_AGE_MS contados de quando o usuário saiu dele, não de
// quando entrou — quem ficou meia hora nas Notas e veio ao FlowVoice agora volta às Notas; quem saiu
// delas ontem não. Passar pelo launcher não apaga o destino, porque os dois caminhos da P130 passam
// por ele: Notas → Início → ícone do FlowVoice, e Notas → Recentes (que no One UI é o launcher) →
// FlowVoice. Sem destino, o Início só manda o FlowVoice para trás e o primeiro trecho espera
// "Inserir aqui".
class PreviousAppTracker(
    private val ownPackage: String,
    private val clockMs: () -> Long
) {
    private var candidate: String? = null
    private var leftAtMs: Long? = null

    val target: String?
        get() {
            val left = leftAtMs ?: return candidate
            if (clockMs() - left > TARGET_MAX_AGE_MS) {
                candidate = null
                leftAtMs = null
            }
            return candidate
        }

    private var expectingExternalApp = false
    private val openedByFlowVoice = mutableSetOf<String>()

    fun noteExternalLaunch() {
        expectingExternalApp = true
    }

    fun onWindowStateChanged(
        packageName: String?,
        homePackages: Set<String>,
        isActiveAppWindow: () -> Boolean,
        isLaunchable: (String) -> Boolean
    ) {
        if (packageName.isNullOrBlank()) return
        if (packageName == candidate) {
            leftAtMs = null
            return
        }
        // Só a tela do próprio FlowVoice conta como saída; a bolha e o cartão da prévia, que também são
        // janelas dele, aparecem por cima do destino sem que o usuário tenha saído.
        if (packageName == ownPackage) {
            if (departurePending() && isActiveAppWindow()) markLeft()
            return
        }
        if (packageName in homePackages) {
            if (isActiveAppWindow()) {
                openedByFlowVoice.clear()
                expectingExternalApp = false
                markLeft()
            }
            return
        }
        if (packageName in openedByFlowVoice || !isActiveAppWindow()) return
        // O seletor de compartilhamento ("android", intentresolver) não tem ícone: não é o app que o
        // FlowVoice abriu, e consumir a marca nele faria do app escolhido (o e-mail) o destino (N4).
        if (!isLaunchable(packageName)) return
        if (expectingExternalApp) {
            openedByFlowVoice += packageName
            expectingExternalApp = false
            markLeft()
            return
        }
        candidate = packageName
        leftAtMs = null
    }

    private fun departurePending(): Boolean = candidate != null && leftAtMs == null

    private fun markLeft() {
        if (departurePending()) leftAtMs = clockMs()
    }

    companion object {
        const val TARGET_MAX_AGE_MS = 10 * 60 * 1_000L
    }
}
