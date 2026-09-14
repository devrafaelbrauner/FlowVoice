package dev.rafaelbrauner.flowvoice.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import dev.rafaelbrauner.flowvoice.ui.components.FvBottomNav
import dev.rafaelbrauner.flowvoice.ui.components.FvTab
import dev.rafaelbrauner.flowvoice.ui.navigation.FvDestination
import dev.rafaelbrauner.flowvoice.ui.screens.diagnostics.DiagnosticsRoute
import dev.rafaelbrauner.flowvoice.ui.screens.dictionary.DictionaryRoute
import dev.rafaelbrauner.flowvoice.ui.screens.home.HomeRoute
import dev.rafaelbrauner.flowvoice.ui.screens.login.LoginRoute
import dev.rafaelbrauner.flowvoice.ui.screens.notes.NotesRoute
import dev.rafaelbrauner.flowvoice.ui.screens.onboarding.OnboardingRoute
import dev.rafaelbrauner.flowvoice.ui.screens.settings.SettingsRoute
import dev.rafaelbrauner.flowvoice.ui.shell.BackStack
import dev.rafaelbrauner.flowvoice.ui.shell.startDestination
import dev.rafaelbrauner.flowvoice.ui.shell.tab
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun FlowVoiceRoot(modifier: Modifier = Modifier) {
    val colors = FlowVoiceTheme.colors
    val preferences = rememberKoin<PreferencesStore>()
    var stack by rememberSaveable(stateSaver = BackStackSaver) {
        mutableStateOf(BackStack.of(startDestination(preferences.read())))
    }
    var noteToOpen by rememberSaveable { mutableStateOf<String?>(null) }
    val current = stack.current
    val tab = current.tab()

    BackHandler(enabled = stack.canPop) { stack = stack.pop() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .then(
                    if (tab == null) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    } else {
                        Modifier.imePadding()
                    }
                )
        ) {
            val screen = Modifier.fillMaxSize()
            when (current) {
                FvDestination.Login -> LoginRoute(
                    onContinue = {
                        preferences.update { copy(loginCompleted = true) }
                        stack = BackStack.of(startDestination(preferences.read()))
                    },
                    modifier = screen
                )

                FvDestination.Onboarding -> OnboardingRoute(
                    onDone = {
                        preferences.update { copy(onboardingCompleted = true) }
                        stack = if (stack.canPop) stack.pop() else BackStack.of(FvDestination.Home)
                    },
                    onOpenKeySettings = { stack = stack.push(FvDestination.Settings) },
                    modifier = screen
                )

                FvDestination.Home -> HomeRoute(
                    onOpenNotes = {
                        noteToOpen = null
                        stack = stack.selectTab(FvTab.Notes)
                    },
                    onOpenNote = { id ->
                        noteToOpen = id
                        stack = stack.selectTab(FvTab.Notes)
                    },
                    onOpenDiagnostics = { stack = stack.push(FvDestination.Diagnostics) },
                    onOpenOnboarding = { stack = stack.push(FvDestination.Onboarding) },
                    modifier = screen
                )

                FvDestination.Notes -> NotesRoute(initialNoteId = noteToOpen, modifier = screen)

                FvDestination.Dictionary -> DictionaryRoute(modifier = screen)

                FvDestination.Settings -> SettingsRoute(
                    onOpenDiagnostics = { stack = stack.push(FvDestination.Diagnostics) },
                    modifier = screen
                )

                FvDestination.Diagnostics -> DiagnosticsRoute(
                    onBack = { stack = if (stack.canPop) stack.pop() else stack.selectTab(FvTab.Settings) },
                    modifier = screen
                )
            }
        }
        if (tab != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.chrome)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                FvBottomNav(
                    selected = tab,
                    onSelect = { selected ->
                        if (selected != FvTab.Notes) noteToOpen = null
                        stack = stack.selectTab(selected)
                    }
                )
            }
        }
    }
}

private fun PreferencesStore.update(transform: AppPreferences.() -> AppPreferences) {
    write(read().transform())
}

private val BackStackSaver = Saver<BackStack, ArrayList<String>>(
    save = { stack -> ArrayList(stack.entries.map { it.name }) },
    restore = { names -> BackStack(names.map { FvDestination.valueOf(it) }) }
)
