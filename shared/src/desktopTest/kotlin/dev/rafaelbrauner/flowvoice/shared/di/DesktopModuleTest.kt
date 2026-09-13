package dev.rafaelbrauner.flowvoice.shared.di

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.JavaSoundAudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.UnsupportedTextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.WindowsSendInputTextInserter
import dev.rafaelbrauner.flowvoice.shared.platform.DesktopOs
import dev.rafaelbrauner.flowvoice.shared.transcription.ProtectedFileSecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.UnavailableSecretStore
import org.koin.dsl.koinApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopModuleTest {
    @Test
    fun windowsUsesSendInputAndDpapiStore() {
        val dataDirectory = Files.createTempDirectory("flowvoice-data")

        val inserter = DesktopBindings.textInserter(DesktopOs.Windows)
        val store = DesktopBindings.secretStore(DesktopOs.Windows, dataDirectory)

        assertIs<WindowsSendInputTextInserter>(inserter)
        assertTrue(inserter.isAvailable)
        assertIs<ProtectedFileSecretStore>(store)
    }

    @Test
    fun otherSystemsNeverInsertNorPersistKey() {
        listOf(DesktopOs.MacOs, DesktopOs.Linux, DesktopOs.Other).forEach { os ->
            val inserter = DesktopBindings.textInserter(os)
            assertIs<UnsupportedTextInserter>(inserter)
            assertFalse(inserter.isAvailable)
            assertIs<UnavailableSecretStore>(DesktopBindings.secretStore(os))
        }
    }

    @Test
    fun koinGraphResolvesDesktopDependencies() {
        val koin = koinApplication { modules(sharedModule, desktopModule(DesktopOs.Linux)) }.koin

        assertIs<JavaSoundAudioCaptureEngine>(koin.get<AudioCaptureEngine>())
        assertIs<UnsupportedTextInserter>(koin.get<TextInserter>())
        assertIs<UnavailableSecretStore>(koin.get<SecretStore>())
        koin.get<DictationSessionController>()
        koin.get<TranscriptionClient>()
        koin.close()
    }
}
