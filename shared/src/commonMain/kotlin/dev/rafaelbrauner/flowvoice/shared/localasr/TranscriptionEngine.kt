package dev.rafaelbrauner.flowvoice.shared.localasr

import kotlinx.serialization.Serializable

// "Motor de transcrição" em Ajustes. Nuvem é o padrão, inclusive para quem já usava o app.
@Serializable
enum class TranscriptionEngine { Cloud, Local }

object TranscriptionEngineSelection {
    // O motor que o próximo ditado usa. O local só vale escolhido, com o modelo inteiro no aparelho e
    // numa plataforma que tem o motor (Android); qualquer outro caso é a nuvem, como antes.
    fun effective(
        choice: TranscriptionEngine,
        modelInstalled: Boolean,
        platformSupportsLocal: Boolean
    ): TranscriptionEngine =
        if (choice == TranscriptionEngine.Local && modelInstalled && platformSupportsLocal) {
            TranscriptionEngine.Local
        } else {
            TranscriptionEngine.Cloud
        }

    // O ditado pode começar: o motor local não precisa da chave OpenRouter; a nuvem precisa.
    fun canDictate(effective: TranscriptionEngine, keyConfigured: Boolean): Boolean =
        effective == TranscriptionEngine.Local || keyConfigured
}
