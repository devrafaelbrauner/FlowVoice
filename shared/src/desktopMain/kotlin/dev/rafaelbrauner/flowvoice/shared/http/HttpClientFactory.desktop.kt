package dev.rafaelbrauner.flowvoice.shared.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java

internal actual fun createDefaultHttpClient(): HttpClient = HttpClient(Java) {
    applySharedConfig()
}