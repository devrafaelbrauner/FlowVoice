package dev.rafaelbrauner.flowvoice.shared

import android.os.Build

actual fun platformName(): String = "Android ${Build.VERSION.RELEASE}"