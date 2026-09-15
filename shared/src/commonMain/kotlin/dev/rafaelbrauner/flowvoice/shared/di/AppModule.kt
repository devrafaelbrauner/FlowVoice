package dev.rafaelbrauner.flowvoice.shared.di

import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApi
import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApiClient
import dev.rafaelbrauner.flowvoice.shared.http.buildHttpClient
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterKeyValidator
import dev.rafaelbrauner.flowvoice.shared.proofreading.OpenRouterProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterTranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.RetryingTranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionEventLog
import io.ktor.client.HttpClient
import org.koin.dsl.module

val sharedModule = module {
    single<HttpClient> { buildHttpClient() }
    single<OpenRouterApi> { OpenRouterApiClient(get()) }
    single { OpenRouterConfig() }
    single { OpenRouterKeyValidator(get(), get()) }
    single<TranscriptionClient> {
        val eventLog = getOrNull<TranscriptionEventLog>() ?: TranscriptionEventLog.NoOp
        RetryingTranscriptionClient(
            delegate = OpenRouterTranscriptionClient(get(), get(), eventLog),
            config = get(),
            eventLog = eventLog
        )
    }
    single<ProofreadingClient> { OpenRouterProofreadingClient(get(), get()) }
}