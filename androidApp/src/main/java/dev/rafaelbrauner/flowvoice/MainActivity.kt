package dev.rafaelbrauner.flowvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
}
