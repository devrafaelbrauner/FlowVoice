package dev.rafaelbrauner.flowvoice.shared.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val sharedJson = Json {
    ignoreUnknownKeys = true
}

internal fun HttpClientConfig<*>.applySharedConfig() {
    install(ContentNegotiation) {
        json(sharedJson)
    }
}

internal expect fun createDefaultHttpClient(): HttpClient

internal fun buildHttpClient(): HttpClient = createDefaultHttpClient()