package dev.rafaelbrauner.flowvoice.shared.di

import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApi
import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApiClient
import dev.rafaelbrauner.flowvoice.shared.http.buildHttpClient
import io.ktor.client.HttpClient
import org.koin.dsl.module

val sharedModule = module {
    single<HttpClient> { buildHttpClient() }
    single<OpenRouterApi> { OpenRouterApiClient(get()) }
}