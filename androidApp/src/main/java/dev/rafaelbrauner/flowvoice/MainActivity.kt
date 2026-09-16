package dev.rafaelbrauner.flowvoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import dev.rafaelbrauner.flowvoice.service.BubblePositionStore
import dev.rafaelbrauner.flowvoice.service.FlowVoiceOverlayService
import dev.rafaelbrauner.flowvoice.service.OverlayAutoStart
import dev.rafaelbrauner.flowvoice.ui.FlowVoiceRoot
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FlowVoiceTheme {
                FlowVoiceRoot()
            }
        }
    }

    // Reinstalar ou atualizar o app mata o processo e a bolha some (P141). Com o app em primeiro
    // plano, o serviço de microfone pode ser iniciado de novo sem esbarrar nas restrições do Android.
    override fun onStart() {
        super.onStart()
        val restart = OverlayAutoStart.shouldRestart(
            enabled = BubblePositionStore(this).readEnabled(),
            running = FlowVoiceOverlayService.running,
            sdkInt = Build.VERSION.SDK_INT,
            canDrawOverlays = Settings.canDrawOverlays(this),
            microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
        if (restart) {
            ContextCompat.startForegroundService(this, Intent(this, FlowVoiceOverlayService::class.java))
        }
    }
}
