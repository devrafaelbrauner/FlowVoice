package dev.rafaelbrauner.flowvoice.shared.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
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
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
        // 12 s de silêncio de leitura: rota travada (conexão aberta, zero bytes) morre ANTES do
        // prazo de 15 s do fim do ditado. No S26 (2026-10-02 03:10–03:19) as três janelas do
        // ditado ficaram sem resposta até o prazo, o usuário esperou "Transcrevendo" sem aviso,
        // e o benchmark 6/6 ok 11 min depois provou que o caminho era a rede do aparelho, não o
        // serviço. Com 12 s a janela falha com o kind de rede e o aviso sai no fim do ditado,
        // em vez de unresolved. O requestTimeout de 30 s segue: cobre respostas lentas mas
        // saudáveis (passada final com áudio de 50 s), que têm leitura contínua e não encostam
        // no silêncio de 12 s.
        socketTimeoutMillis = 12_000
    }
}

internal expect fun createDefaultHttpClient(): HttpClient

internal fun buildHttpClient(): HttpClient = createDefaultHttpClient()