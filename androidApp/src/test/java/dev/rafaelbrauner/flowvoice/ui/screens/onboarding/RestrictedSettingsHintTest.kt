package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestrictedSettingsHintTest {

    @Test
    fun adbInstallOnAndroid13WithInactiveServiceShowsHint() {
        assertTrue(RestrictedSettingsHint.shouldShow(sdkInt = 33, accessibilityActive = false, installer = "com.android.shell"))
        assertTrue(RestrictedSettingsHint.shouldShow(sdkInt = 36, accessibilityActive = false, installer = null))
    }

    @Test
    fun apkOpenedFromFilesShowsHint() {
        assertTrue(
            RestrictedSettingsHint.shouldShow(
                sdkInt = 34,
                accessibilityActive = false,
                installer = "com.google.android.packageinstaller"
            )
        )
    }

    @Test
    fun activeServiceHidesHint() {
        assertFalse(RestrictedSettingsHint.shouldShow(sdkInt = 36, accessibilityActive = true, installer = null))
    }

    @Test
    fun androidBelow13HasNoRestrictedSettings() {
        assertFalse(RestrictedSettingsHint.shouldShow(sdkInt = 32, accessibilityActive = false, installer = null))
    }

    @Test
    fun storeInstallHidesHint() {
        assertFalse(RestrictedSettingsHint.shouldShow(sdkInt = 36, accessibilityActive = false, installer = "com.android.vending"))
        assertFalse(
            RestrictedSettingsHint.shouldShow(sdkInt = 36, accessibilityActive = false, installer = "com.sec.android.app.samsungapps")
        )
    }
}
