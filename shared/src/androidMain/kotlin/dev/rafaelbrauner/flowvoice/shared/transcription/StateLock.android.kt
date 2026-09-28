package dev.rafaelbrauner.flowvoice.shared.transcription

internal actual inline fun <T> locked(lock: Any, block: () -> T): T = synchronized(lock, block)
