package dev.rafaelbrauner.flowvoice.shared.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

internal actual fun createDefaultHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        // Sem ping, uma conexão HTTP/2 que parou de responder segura todos os pedidos do processo até
        // o timeout de 30 s — mais que o prazo do ditado (NV-rede, S26 29/set: nenhuma janela respondeu
        // até reiniciar o processo). Com ping, a conexão morta falha em segundos e o pedido refeito
        // abre outra.
        config { pingInterval(PING_INTERVAL_S, TimeUnit.SECONDS) }
    }
    applySharedConfig()
}

private const val PING_INTERVAL_S = 5L
