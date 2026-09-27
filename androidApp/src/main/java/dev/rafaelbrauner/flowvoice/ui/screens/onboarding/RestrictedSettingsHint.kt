package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

object RestrictedSettingsHint {
    const val SDK_RESTRICTED_SETTINGS = 33
    const val TITLE = "O Android vai bloquear na primeira vez. É esperado:"
    val STEPS = listOf(
        "Toque no passo 1 e tente ligar o FlowVoice. O Android vai dizer que o acesso foi negado e que isso " +
            "põe suas informações em risco: é o aviso padrão para qualquer app instalado fora de uma loja.",
        "Toque em “Abrir detalhes do app”, aqui embaixo, depois em ⋮ (canto de cima) e em " +
            "“Permitir configurações restritas”. O Android pode pedir seu PIN.",
        "Volte ao FlowVoice e toque no passo 1 de novo."
    )
    const val ACTION = "Abrir detalhes do app"

    private val STORE_INSTALLERS = setOf("com.android.vending", "com.sec.android.app.samsungapps")

    fun shouldShow(sdkInt: Int, accessibilityActive: Boolean, installer: String?): Boolean =
        sdkInt >= SDK_RESTRICTED_SETTINGS && !accessibilityActive && installer !in STORE_INSTALLERS
}
