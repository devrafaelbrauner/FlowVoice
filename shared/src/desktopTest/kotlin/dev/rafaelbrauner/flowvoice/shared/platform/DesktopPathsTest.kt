package dev.rafaelbrauner.flowvoice.shared.platform

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPathsTest {
    private val home = "/home/rafael"

    @Test
    fun windowsUsesLocalAppDataNotRoaming() {
        val env = mapOf("LOCALAPPDATA" to "C:/Users/rafael/AppData/Local", "APPDATA" to "C:/Users/rafael/AppData/Roaming")

        val dir = DesktopPaths.localDataDirectory(DesktopOs.Windows, env::get, home)

        assertEquals(Paths.get("C:/Users/rafael/AppData/Local", "FlowVoice"), dir)
    }

    @Test
    fun windowsFallsBackToHomeWhenLocalAppDataMissing() {
        val dir = DesktopPaths.localDataDirectory(DesktopOs.Windows, { null }, home)

        assertEquals(Paths.get(home, "AppData", "Local", "FlowVoice"), dir)
    }

    @Test
    fun macAndLinuxUsePlatformConventions() {
        assertEquals(
            Paths.get(home, "Library", "Application Support", "FlowVoice"),
            DesktopPaths.localDataDirectory(DesktopOs.MacOs, { null }, home)
        )
        assertEquals(
            Paths.get("/data", "FlowVoice"),
            DesktopPaths.localDataDirectory(DesktopOs.Linux, mapOf("XDG_DATA_HOME" to "/data")::get, home)
        )
        assertEquals(
            Paths.get(home, ".local", "share", "FlowVoice"),
            DesktopPaths.localDataDirectory(DesktopOs.Linux, { " " }, home)
        )
    }
}
