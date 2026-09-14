package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

object RestrictedSettingsHint {
    const val SDK_RESTRICTED_SETTINGS = 33
    const val MESSAGE =
        "Se o interruptor não ligar: Configurações → Aplicativos → FlowVoice → ⋮ → Permitir configurações restritas"
    const val ACTION = "abrir detalhes do app"

    private val STORE_INSTALLERS = setOf("com.android.vending", "com.sec.android.app.samsungapps")

    fun shouldShow(sdkInt: Int, accessibilityActive: Boolean, installer: String?): Boolean =
        sdkInt >= SDK_RESTRICTED_SETTINGS && !accessibilityActive && installer !in STORE_INSTALLERS
}
