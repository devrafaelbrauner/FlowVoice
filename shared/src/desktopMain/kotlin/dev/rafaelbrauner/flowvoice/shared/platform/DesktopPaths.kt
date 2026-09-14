package dev.rafaelbrauner.flowvoice.shared.platform

import java.nio.file.Path
import java.nio.file.Paths

object DesktopPaths {
    const val APP_DIRECTORY = "FlowVoice"

    fun localDataDirectory(
        os: DesktopOs,
        env: (String) -> String? = System::getenv,
        userHome: String = System.getProperty("user.home")
    ): Path = when (os) {
        DesktopOs.Windows -> env("LOCALAPPDATA").nonBlankPath()
            ?: Paths.get(userHome, "AppData", "Local", APP_DIRECTORY)
        DesktopOs.MacOs -> Paths.get(userHome, "Library", "Application Support", APP_DIRECTORY)
        DesktopOs.Linux,
        DesktopOs.Other -> env("XDG_DATA_HOME").nonBlankPath()
            ?: Paths.get(userHome, ".local", "share", APP_DIRECTORY)
    }

    private fun String?.nonBlankPath(): Path? =
        this?.takeIf { it.isNotBlank() }?.let { Paths.get(it, APP_DIRECTORY) }
}
