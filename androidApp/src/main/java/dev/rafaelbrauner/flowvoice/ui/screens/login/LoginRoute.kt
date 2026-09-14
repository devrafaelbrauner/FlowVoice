package dev.rafaelbrauner.flowvoice.ui.screens.login

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.rafaelbrauner.flowvoice.auth.GoogleSignInHelper
import dev.rafaelbrauner.flowvoice.shared.auth.AuthGateway
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.components.Wordmark
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.shell.findActivity
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "FlowVoiceLogin"

@Composable
fun LoginRoute(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = rememberKoin<PreferencesStore>()
    val auth = rememberKoin<AuthGateway>()
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LoginScreen(
        signingIn = signingIn,
        failed = failed,
        onContinueWithGoogle = {
            val clientId = preferences.read().googleWebClientId
            val activity = context.findActivity()
            if (clientId.isBlank() || activity == null) {
                onContinue()
            } else if (!signingIn) {
                signingIn = true
                failed = false
                scope.launch {
                    try {
                        auth.signIn(GoogleSignInHelper.signIn(activity, clientId))
                        onContinue()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(TAG, "google_sign_in_failed ${error.javaClass.simpleName}")
                        failed = true
                    } finally {
                        signingIn = false
                    }
                }
            }
        },
        onSkip = onContinue,
        modifier = modifier
    )
}

@Composable
internal fun LoginScreen(
    signingIn: Boolean,
    failed: Boolean,
    onContinueWithGoogle: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = modifier
            .background(colors.background)
            .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Wordmark()
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Sua voz,\nno campo certo.",
            style = typography.loginTitle,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Entre com o Google para sincronizar notas, dicionário e preferências. " +
                "Sua chave OpenRouter fica só neste aparelho.",
            style = typography.body,
            color = colors.textMuted,
            modifier = Modifier.widthIn(max = 280.dp)
        )
        Spacer(Modifier.height(32.dp))
        GoogleButton(
            label = if (signingIn) "Entrando…" else "Continuar com o Google",
            enabled = !signingIn,
            onClick = onContinueWithGoogle
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Uso pessoal · pt-BR · dados seus, servidor seu",
            style = typography.bodySmall.copy(lineHeight = 1.5.em),
            color = colors.textTimestamp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (failed) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Não foi possível entrar com o Google.",
                style = typography.bodySmall,
                color = colors.destructiveText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FlowVoiceSpacing.minTouchTarget)
                    .clickable(role = Role.Button, onClick = onSkip),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "continuar sem entrar",
                    style = typography.monoValue,
                    color = colors.accentText
                )
            }
        }
    }
}

@Composable
private fun GoogleButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(FlowVoiceRadius.loginButton)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(if (pressed) colors.textPrimary.copy(alpha = 0.92f) else colors.textPrimary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.background)
        )
        Text(text = label, style = FlowVoiceTheme.typography.buttonLarge, color = colors.background)
    }
}

@Preview(heightDp = 760)
@Composable
private fun LoginScreenPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        LoginScreen(
            signingIn = false,
            failed = dark.not(),
            onContinueWithGoogle = {},
            onSkip = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
