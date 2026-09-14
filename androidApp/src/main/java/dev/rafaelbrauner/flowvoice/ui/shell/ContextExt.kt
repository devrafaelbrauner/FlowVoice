package dev.rafaelbrauner.flowvoice.ui.shell

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

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
    true
} catch (_: ActivityNotFoundException) {
    false
}
