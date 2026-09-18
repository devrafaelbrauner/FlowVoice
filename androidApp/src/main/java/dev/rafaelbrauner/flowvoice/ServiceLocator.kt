package dev.rafaelbrauner.flowvoice

import android.content.Context
import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApi
import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApiClient
import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterTranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.http.buildHttpClient
import dev.rafaelbrauner.flowvoice.shared.security.AndroidApiKeyStore
import dev.rafaelbrauner.flowvoice.shared.security.ApiKeyStore
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionApi
import io.ktor.client.HttpClient

object ServiceLocator {

    @Volatile
    private var http: HttpClient? = null

    fun httpClient(): HttpClient =
        http ?: synchronized(this) {
            http ?: buildHttpClient().also { http = it }
        }

    fun transcriptionApi(context: Context): TranscriptionApi =
        OpenRouterTranscriptionClient(httpClient())

    fun openRouterApi(): OpenRouterApi =
        OpenRouterApiClient(httpClient())

    fun apiKeyStore(context: Context): ApiKeyStore =
        AndroidApiKeyStore(context.applicationContext)

    fun modelStore(context: Context): ModelStore =
        SharedPrefsModelStore(context.applicationContext)
}
