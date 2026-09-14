package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FocusedPackageTest {
    @Test
    fun staleEditorFromPreviousAppLosesToActiveWindow() {
        assertEquals(
            "com.sec.android.app.launcher",
            FocusedPackage.resolve(
                editorPackage = "com.whatsapp",
                inputStarted = true,
                activeWindowPackage = "com.sec.android.app.launcher"
            )
        )
    }

    @Test
    fun editorWithoutStartedInputIsIgnored() {
        assertEquals(
            "com.android.chrome",
            FocusedPackage.resolve(
                editorPackage = "com.whatsapp",
                inputStarted = false,
                activeWindowPackage = "com.android.chrome"
            )
        )
        assertNull(FocusedPackage.resolve(editorPackage = "com.whatsapp", inputStarted = false, activeWindowPackage = null))
    }

    @Test
    fun matchingStartedEditorConfirmsActiveWindow() {
        assertEquals(
            "com.whatsapp",
            FocusedPackage.resolve(editorPackage = "com.whatsapp", inputStarted = true, activeWindowPackage = "com.whatsapp")
        )
    }

    @Test
    fun startedEditorIsUsedOnlyWhenActiveWindowIsUnknown() {
        assertEquals(
            "com.whatsapp",
            FocusedPackage.resolve(editorPackage = "com.whatsapp", inputStarted = true, activeWindowPackage = null)
        )
    }

    @Test
    fun nothingKnownResolvesToNull() {
        assertNull(FocusedPackage.resolve(editorPackage = null, inputStarted = false, activeWindowPackage = null))
        assertNull(FocusedPackage.resolve(editorPackage = null, inputStarted = true, activeWindowPackage = null))
    }
}
