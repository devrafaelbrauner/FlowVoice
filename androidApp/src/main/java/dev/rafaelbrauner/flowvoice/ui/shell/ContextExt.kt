package dev.rafaelbrauner.flowvoice.ui.shell

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object ExternalLaunch {
    fun flagsFor(currentFlags: Int, targetPackage: String?, ownPackage: String): Int =
        if (targetPackage == ownPackage) currentFlags else currentFlags or Intent.FLAG_ACTIVITY_NEW_TASK
}

fun Context.startActivitySafely(intent: Intent): Boolean = try {
    val target = intent.component?.packageName ?: intent.`package`
    intent.flags = ExternalLaunch.flagsFor(intent.flags, target, packageName)
    startActivity(intent)
    // Ajustes, compartilhamento e afins abertos daqui não viram destino do retorno do Início (P130).
    if (target != packageName) FlowVoiceAccessibilityService.service?.noteExternalLaunch()
    true
} catch (_: ActivityNotFoundException) {
    false
}

// Até o Android 10 o `package:` abre a página do FlowVoice; do 11 em diante o sistema ignora o pacote
// e sempre mostra a lista de apps (mudança documentada da plataforma), então o usuário escolhe o app lá.
fun Context.openOverlayPermissionSettings() {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
    if (!startActivitySafely(intent)) startActivitySafely(Intent(Settings.ACTION_SETTINGS))
}
