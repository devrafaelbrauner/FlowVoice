package dev.rafaelbrauner.flowvoice.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.rafaelbrauner.flowvoice.shared.di.desktopModule
import dev.rafaelbrauner.flowvoice.shared.di.sharedModule
import org.koin.core.context.startKoin

fun main() {
    startKoin { modules(sharedModule, desktopModule()) }
    application {
        val controller = remember { DesktopDictationController() }
        val windowState = rememberWindowState(size = DpSize(560.dp, 760.dp))
        Window(
            onCloseRequest = {
                controller.close()
                exitApplication()
            },
            state = windowState,
            title = "FlowVoice"
        ) {
            FlowVoiceDesktopScreen(controller, hideWindow = { windowState.isMinimized = true })
        }
    }
}
