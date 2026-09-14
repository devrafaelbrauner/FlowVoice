package dev.rafaelbrauner.flowvoice.ui.shell

import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.ui.components.FvTab
import dev.rafaelbrauner.flowvoice.ui.navigation.FvDestination

fun startDestination(preferences: AppPreferences): FvDestination = when {
    !preferences.loginCompleted -> FvDestination.Login
    !preferences.onboardingCompleted -> FvDestination.Onboarding
    else -> FvDestination.Home
}

fun FvDestination.tab(): FvTab? = when (this) {
    FvDestination.Home -> FvTab.Home
    FvDestination.Notes -> FvTab.Notes
    FvDestination.Dictionary -> FvTab.Dictionary
    FvDestination.Settings, FvDestination.Diagnostics -> FvTab.Settings
    FvDestination.Login, FvDestination.Onboarding -> null
}

fun FvTab.destination(): FvDestination = when (this) {
    FvTab.Home -> FvDestination.Home
    FvTab.Notes -> FvDestination.Notes
    FvTab.Dictionary -> FvDestination.Dictionary
    FvTab.Settings -> FvDestination.Settings
}

data class BackStack(val entries: List<FvDestination>) {
    init {
        require(entries.isNotEmpty()) { "a pilha de navegação não pode ficar vazia" }
    }

    val current: FvDestination
        get() = entries.last()

    val canPop: Boolean
        get() = entries.size > 1

    fun push(destination: FvDestination): BackStack =
        if (current == destination) this else BackStack(entries + destination)

    fun pop(): BackStack = if (canPop) BackStack(entries.dropLast(1)) else this

    fun selectTab(tab: FvTab): BackStack = BackStack(
        if (tab == FvTab.Home) listOf(FvDestination.Home) else listOf(FvDestination.Home, tab.destination())
    )

    companion object {
        fun of(destination: FvDestination): BackStack = BackStack(listOf(destination))
    }
}
