package dev.rafaelbrauner.flowvoice.shared.di

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindowAggregator
import dev.rafaelbrauner.flowvoice.shared.dictation.JavaSoundAudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.SpeechEndpointing
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.UnsupportedTextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.WindowsSendInputTextInserter
import dev.rafaelbrauner.flowvoice.shared.platform.DesktopOs
import dev.rafaelbrauner.flowvoice.shared.platform.DesktopPaths
import dev.rafaelbrauner.flowvoice.shared.transcription.DpapiSecretCipher
import dev.rafaelbrauner.flowvoice.shared.transcription.ProtectedFileSecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.UnavailableSecretStore
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Path

object DesktopBindings {
    const val OPENROUTER_KEY_FILE = "openrouter-key.dpapi"

    fun textInserter(os: DesktopOs): TextInserter = when (os) {
        DesktopOs.Windows -> WindowsSendInputTextInserter()
        DesktopOs.MacOs,
        DesktopOs.Linux,
        DesktopOs.Other -> UnsupportedTextInserter(os.name)
    }

    fun secretStore(
        os: DesktopOs,
        dataDirectory: Path = DesktopPaths.localDataDirectory(os)
    ): SecretStore = when (os) {
        DesktopOs.Windows -> ProtectedFileSecretStore(dataDirectory.resolve(OPENROUTER_KEY_FILE), DpapiSecretCipher())
        DesktopOs.MacOs,
        DesktopOs.Linux,
        DesktopOs.Other -> UnavailableSecretStore("sem cofre de chaves para ${os.name}; a chave OpenRouter não é salva")
    }
}

fun desktopModule(os: DesktopOs = DesktopOs.current()): Module = module {
    single<AudioCaptureEngine> { JavaSoundAudioCaptureEngine() }
    factory {
        DictationSessionController(
            get(),
            windowPauseSearchBeforeMs = DictationWindowAggregator.SPEECH_PAUSE_SEARCH_BEFORE_MS,
            windowPauseSearchAfterMs = DictationWindowAggregator.SPEECH_PAUSE_SEARCH_AFTER_MS,
            windowEndpointing = SpeechEndpointing(),
            windowContextDurationMs = DictationWindowAggregator.SPEECH_CONTEXT_MS
        )
    }
    single<SecretStore> { DesktopBindings.secretStore(os) }
    single<TextInserter> { DesktopBindings.textInserter(os) }
}
