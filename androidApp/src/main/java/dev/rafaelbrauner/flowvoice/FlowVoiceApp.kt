package dev.rafaelbrauner.flowvoice

import android.app.Application
import dev.rafaelbrauner.flowvoice.shared.dictation.AndroidAudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.di.sharedModule
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

private val dictationModule = module {
    single<AudioCaptureEngine> { AndroidAudioCaptureEngine(androidContext()) }
    factory { DictationSessionController(get()) }
}