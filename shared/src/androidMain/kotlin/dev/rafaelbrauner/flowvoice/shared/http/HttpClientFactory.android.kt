package dev.rafaelbrauner.flowvoice.shared.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createDefaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    applySharedConfig()
}