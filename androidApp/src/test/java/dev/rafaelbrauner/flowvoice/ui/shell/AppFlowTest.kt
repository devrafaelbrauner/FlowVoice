package dev.rafaelbrauner.flowvoice.ui.shell

import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.ui.components.FvTab
import dev.rafaelbrauner.flowvoice.ui.navigation.FvDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppFlowTest {

    @Test
    fun firstLaunchStartsAtLogin() {
        assertEquals(FvDestination.Login, startDestination(AppPreferences()))
    }

    @Test
    fun afterLoginStartsAtOnboarding() {
        assertEquals(FvDestination.Onboarding, startDestination(AppPreferences(loginCompleted = true)))
    }

    @Test
    fun completedFlowStartsAtHome() {
        val preferences = AppPreferences(loginCompleted = true, onboardingCompleted = true)
        assertEquals(FvDestination.Home, startDestination(preferences))
    }

    @Test
    fun onboardingWithoutLoginStillAsksLoginFirst() {
        assertEquals(FvDestination.Login, startDestination(AppPreferences(onboardingCompleted = true)))
    }

    @Test
    fun diagnosticsKeepsSettingsTabActive() {
        assertEquals(FvTab.Settings, FvDestination.Diagnostics.tab())
        assertEquals(FvTab.Settings, FvDestination.Settings.tab())
    }

    @Test
    fun loginAndOnboardingHaveNoBottomNav() {
        assertNull(FvDestination.Login.tab())
        assertNull(FvDestination.Onboarding.tab())
    }

    @Test
    fun selectingTabResetsStackOnTopOfHome() {
        val deep = BackStack(listOf(FvDestination.Home, FvDestination.Settings, FvDestination.Diagnostics))

        assertEquals(listOf(FvDestination.Home, FvDestination.Notes), deep.selectTab(FvTab.Notes).entries)
        assertEquals(listOf(FvDestination.Home), deep.selectTab(FvTab.Home).entries)
    }

    @Test
    fun backFromDiagnosticsReturnsToWhereItWasOpened() {
        val fromHome = BackStack.of(FvDestination.Home).push(FvDestination.Diagnostics)
        val fromSettings = BackStack.of(FvDestination.Home).selectTab(FvTab.Settings).push(FvDestination.Diagnostics)

        assertEquals(FvDestination.Home, fromHome.pop().current)
        assertEquals(FvDestination.Settings, fromSettings.pop().current)
    }

    @Test
    fun pushingCurrentDestinationIsNoOp() {
        val stack = BackStack.of(FvDestination.Home)
        assertSame(stack, stack.push(FvDestination.Home))
    }

    @Test
    fun popAtRootKeepsStack() {
        val root = BackStack.of(FvDestination.Onboarding)
        assertFalse(root.canPop)
        assertSame(root, root.pop())
    }
}
