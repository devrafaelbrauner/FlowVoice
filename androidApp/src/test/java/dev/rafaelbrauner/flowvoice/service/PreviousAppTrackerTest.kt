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

    private var nowMs = 0L
    private val tracker = PreviousAppTracker(own) { nowMs }
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
    fun theCurrentTargetAndRepeatedFlowVoiceWindowsDoNotQueryTheWindowList() {
        window(notes)
        val afterFirst = windowQueries

        window(notes)
        window(null)
        assertEquals(afterFirst, windowQueries)

        window(own)
        window(own)
        window(own)
        assertEquals(afterFirst + 1, windowQueries)
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

    private fun minutes(value: Int) = value * 60_000L

    @Test
    fun homeMicReturnsToTheAppLeftThroughTheLauncherAndTheFlowVoiceIcon() {
        window(notes)
        nowMs += minutes(30)
        window(launcher)
        nowMs += 5_000
        window(own)

        assertEquals(notes, tracker.target)
    }

    @Test
    fun homeMicReturnsToTheAppLeftThroughRecentsHostedByTheLauncher() {
        window(notes)
        nowMs += minutes(2)
        window(launcher)
        window(own)

        assertEquals(notes, tracker.target)
    }

    @Test
    fun targetLeftLongerAgoThanTheLimitIsForgotten() {
        window(notes)
        window(launcher)
        nowMs += PreviousAppTracker.TARGET_MAX_AGE_MS + 1
        window(own)

        assertNull(tracker.target)
    }

    @Test
    fun ageCountsFromLeavingTheTargetNotFromOpeningIt() {
        window(notes)
        nowMs += minutes(40)
        window(own)
        nowMs += PreviousAppTracker.TARGET_MAX_AGE_MS - 1

        assertEquals(notes, tracker.target)
        nowMs += 2
        assertNull(tracker.target)
    }

    @Test
    fun comingBackToTheTargetRestartsTheClock() {
        window(notes)
        window(launcher)
        nowMs += minutes(9)
        window(notes)
        nowMs += minutes(9)
        window(launcher)
        nowMs += minutes(9)

        assertEquals(notes, tracker.target)
    }

    @Test
    fun flowVoiceOverlayWindowsOverTheTargetAreNotALeave() {
        window(notes)
        window(own, activeAppWindow = false)
        nowMs += minutes(30)
        window(launcher)
        window(own)

        assertEquals(notes, tracker.target)
    }

    @Test
    fun shareChooserDoesNotMakeTheChosenAppTheTarget() {
        val chooser = "com.android.intentresolver"
        window(notes)
        window(own)
        tracker.noteExternalLaunch()
        window(chooser)
        window("com.whatsapp")
        window(own)

        assertEquals(notes, tracker.target)
    }
}
