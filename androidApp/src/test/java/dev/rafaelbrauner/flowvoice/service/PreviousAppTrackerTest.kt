package dev.rafaelbrauner.flowvoice.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreviousAppTrackerTest {
    private val own = "dev.rafaelbrauner.flowvoice"
    private val launcher = "com.sec.android.app.launcher"
    private val notes = "com.samsung.android.app.notes"
    private val settings = "com.android.settings"
    private val launchable = setOf(notes, settings, "com.whatsapp")

    private val tracker = PreviousAppTracker(own)
    private var windowQueries = 0

    private fun window(pkg: String?, activeAppWindow: Boolean = true) =
        tracker.onWindowStateChanged(
            packageName = pkg,
            homePackages = setOf(launcher),
            isActiveAppWindow = { windowQueries++; activeAppWindow },
            isLaunchable = { it in launchable }
        )

    @Test
    fun launchableAppInTheActiveApplicationWindowBecomesTheTarget() {
        window(notes)
        assertEquals(notes, tracker.target)

        window("com.whatsapp")
        assertEquals("com.whatsapp", tracker.target)
    }

    @Test
    fun flowVoiceTheLauncherAndPackagesWithoutLauncherEntryNeverBecomeTheTarget() {
        window(notes)
        window(own)
        window(launcher)
        window("com.android.systemui")

        assertEquals(notes, tracker.target)
    }

    @Test
    fun overlayKeyboardOrShadeWindowThatIsNotTheActiveAppWindowIsIgnored() {
        window(notes)
        window("com.whatsapp", activeAppWindow = false)

        assertEquals(notes, tracker.target)
    }

    @Test
    fun flowVoiceItselfAndTheCurrentTargetNeverQueryTheWindowList() {
        window(notes)
        val afterFirst = windowQueries

        window(own)
        window(notes)
        window(null)

        assertEquals(afterFirst, windowQueries)
    }

    @Test
    fun appOpenedByFlowVoiceIsIgnoredUntilTheUserGoesThroughTheLauncher() {
        window(notes)
        window(own)
        tracker.noteExternalLaunch()
        window(settings)
        window(own)
        assertEquals(notes, tracker.target)

        window(launcher)
        window(settings)
        assertEquals(settings, tracker.target)
    }

    @Test
    fun launcherWindowThatIsNotActiveDoesNotForgetAppsOpenedByFlowVoice() {
        window(notes)
        tracker.noteExternalLaunch()
        window(settings)
        window(launcher, activeAppWindow = false)
        window(settings)

        assertEquals(notes, tracker.target)
    }

    @Test
    fun nothingIsTrackedBeforeAnyAppWindow() {
        window(own)
        window(launcher)

        assertNull(tracker.target)
    }
}
