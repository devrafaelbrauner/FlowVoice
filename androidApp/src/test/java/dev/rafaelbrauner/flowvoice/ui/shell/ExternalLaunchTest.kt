package dev.rafaelbrauner.flowvoice.ui.shell

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalLaunchTest {
    private val ownPackage = "dev.rafaelbrauner.flowvoice"

    @Test
    fun systemScreenWithoutTargetOpensInItsOwnTask() {
        val flags = ExternalLaunch.flagsFor(currentFlags = 0, targetPackage = null, ownPackage = ownPackage)

        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun otherAppOpensInItsOwnTaskKeepingExistingFlags() {
        val flags = ExternalLaunch.flagsFor(
            currentFlags = Intent.FLAG_ACTIVITY_NO_HISTORY,
            targetPackage = "com.android.settings",
            ownPackage = ownPackage
        )

        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY, flags)
    }

    @Test
    fun ownActivityStaysInTheAppTask() {
        val flags = ExternalLaunch.flagsFor(currentFlags = 0, targetPackage = ownPackage, ownPackage = ownPackage)

        assertEquals(0, flags)
    }
}
