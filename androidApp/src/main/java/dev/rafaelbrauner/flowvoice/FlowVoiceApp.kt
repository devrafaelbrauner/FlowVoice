package dev.rafaelbrauner.flowvoice

import android.app.Application
import android.util.Log
import dev.rafaelbrauner.flowvoice.service.AccessibilityTextInserter
import dev.rafaelbrauner.flowvoice.shared.dictation.AndroidAudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.di.sharedModule
import dev.rafaelbrauner.flowvoice.shared.dictionary.InMemoryPersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.dictionary.PersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.dictionary.PrefsDictionaryPersist
import dev.rafaelbrauner.flowvoice.shared.auth.AuthGateway
import dev.rafaelbrauner.flowvoice.shared.auth.PrefsAuthGateway
import dev.rafaelbrauner.flowvoice.shared.notes.InMemoryNoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.NoteDictationCoordinator
import dev.rafaelbrauner.flowvoice.shared.notes.NoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.PrefsNotePersist
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.shared.prefs.PrefsPreferencesStore
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import dev.rafaelbrauner.flowvoice.shared.sync.InMemoryRemoteSync
import dev.rafaelbrauner.flowvoice.shared.sync.RemoteSync
import dev.rafaelbrauner.flowvoice.shared.sync.SyncEngine
import dev.rafaelbrauner.flowvoice.shared.transcription.EncryptedSecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.ui.screens.diagnostics.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

class FlowVoiceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        GlobalContext.startKoin {
            androidContext(this@FlowVoiceApp)
            modules(sharedModule, dictationModule)
        }
    }
}

private const val DICTATION_TAG = "FlowVoiceDictation"

private val dictationModule = module {
    single<AudioCaptureEngine> { AndroidAudioCaptureEngine(androidContext()) }
    factory { DictationSessionController(get()) }
    single<SecretStore> { EncryptedSecretStore(androidContext()) }
    single<PersonalDictionary> { InMemoryPersonalDictionary(PrefsDictionaryPersist(androidContext())) }
    single<NoteStore> {
        InMemoryNoteStore(
            persist = PrefsNotePersist(androidContext()),
            clock = { System.currentTimeMillis() }
        )
    }
    single<PreferencesStore> { PrefsPreferencesStore(androidContext()) }
    single<AuthGateway> { PrefsAuthGateway(androidContext()) }
    single<RemoteSync> { InMemoryRemoteSync() }
    single {
        SyncEngine(
            auth = get(),
            notes = get(),
            dictionary = get(),
            preferences = get(),
            remote = get(),
            clock = { System.currentTimeMillis() }
        )
    }
    single {
        DictationPipeline(
            controller = get(),
            client = get(),
            config = get(),
            dictionary = get(),
            proofreading = get(),
            preferences = get(),
            secrets = get(),
            inserter = AccessibilityTextInserter,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            eventLog = { event, metadata ->
                Log.i(DICTATION_TAG, metadata.entries.joinToString(" ", prefix = "$event ") { "${it.key}=${it.value}" })
            }
        )
    }
    single(createdAtStart = true) {
        NoteDictationCoordinator(get()).also { coordinator ->
            coordinator.attach(
                get<DictationPipeline>().session,
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            )
        }
    }
    single(createdAtStart = true) {
        DiagnosticsLog().also { log ->
            log.attach(get<DictationPipeline>().events, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        }
    }
}
