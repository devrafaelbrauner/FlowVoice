package dev.rafaelbrauner.flowvoice.service

// App para onde o microfone do Início volta (P130). Medido no S26: a janela de uma activity que
// abre chega no evento já como janela ativa do tipo aplicação; bolhas de outros apps, teclado,
// cortina e painéis do launcher não. Por isso o pacote do evento só conta quando a janela ativa é
// a dele (consulta ao system_server, sem tocar no processo do app), e só depois das saídas baratas.
// Apps que o próprio FlowVoice abriu (Ajustes, compartilhamento) não viram destino até o usuário
// passar pelo launcher.
class PreviousAppTracker(private val ownPackage: String) {
    var target: String? = null
        private set

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
        if (packageName.isNullOrBlank() || packageName == ownPackage || packageName == target) return
        if (packageName in homePackages) {
            if (isActiveAppWindow()) {
                openedByFlowVoice.clear()
                expectingExternalApp = false
            }
            return
        }
        if (packageName in openedByFlowVoice || !isActiveAppWindow()) return
        if (expectingExternalApp) {
            openedByFlowVoice += packageName
            expectingExternalApp = false
            return
        }
        if (isLaunchable(packageName)) target = packageName
    }
}
