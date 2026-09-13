package dev.rafaelbrauner.flowvoice

import android.app.Application
import dev.rafaelbrauner.flowvoice.shared.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FlowVoiceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FlowVoiceApp)
            modules(sharedModule)
        }
    }
}