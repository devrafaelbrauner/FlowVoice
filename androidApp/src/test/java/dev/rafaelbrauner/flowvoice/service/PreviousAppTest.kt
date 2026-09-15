package dev.rafaelbrauner.flowvoice.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreviousAppTest {
    private val own = "dev.rafaelbrauner.flowvoice"
    private val home = setOf("com.sec.android.app.launcher")
    private val launchable = setOf("com.samsung.android.app.notes", "com.whatsapp")

    private fun next(current: String?, active: String?) =
        PreviousApp.next(current, active, own, home) { it in launchable }

    @Test
    fun launchableAppInTheActiveWindowBecomesTheReturnTarget() {
        assertEquals("com.samsung.android.app.notes", next(null, "com.samsung.android.app.notes"))
        assertEquals("com.whatsapp", next("com.samsung.android.app.notes", "com.whatsapp"))
    }

    @Test
    fun flowVoiceItselfTheLauncherAndRecentsNeverReplaceTheAppTheUserCameFrom() {
        assertEquals("com.samsung.android.app.notes", next("com.samsung.android.app.notes", own))
        assertEquals("com.samsung.android.app.notes", next("com.samsung.android.app.notes", "com.sec.android.app.launcher"))
    }

    @Test
    fun unknownWindowOrPackageWithoutLauncherEntryKeepsTheCurrentTarget() {
        assertEquals("com.samsung.android.app.notes", next("com.samsung.android.app.notes", null))
        assertEquals("com.samsung.android.app.notes", next("com.samsung.android.app.notes", "com.android.systemui"))
        assertNull(next(null, "com.android.systemui"))
    }
}
