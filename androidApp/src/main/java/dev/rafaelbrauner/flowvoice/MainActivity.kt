package dev.rafaelbrauner.flowvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafaelbrauner.flowvoice.shared.APP_NAME
import dev.rafaelbrauner.flowvoice.shared.APP_VERSION_NAME
import dev.rafaelbrauner.flowvoice.shared.greeting
import dev.rafaelbrauner.flowvoice.shared.platformName

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = APP_NAME,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = greeting(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${platformName()} — v$APP_VERSION_NAME",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}