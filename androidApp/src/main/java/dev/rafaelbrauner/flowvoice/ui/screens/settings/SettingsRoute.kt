package dev.rafaelbrauner.flowvoice.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.rafaelbrauner.flowvoice.auth.GoogleSignInHelper
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import dev.rafaelbrauner.flowvoice.service.FlowVoiceOverlayService
import dev.rafaelbrauner.flowvoice.shared.auth.AuthGateway
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import dev.rafaelbrauner.flowvoice.shared.sync.SyncEngine
import dev.rafaelbrauner.flowvoice.shared.transcription.KeyValidationResult
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterKeyValidator
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStoreUnavailableException
import dev.rafaelbrauner.flowvoice.ui.components.FvCard
import dev.rafaelbrauner.flowvoice.ui.components.FvDivider
import dev.rafaelbrauner.flowvoice.ui.components.FvToggle
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.components.PillButtonVariant
import dev.rafaelbrauner.flowvoice.ui.components.SettingsCard
import dev.rafaelbrauner.flowvoice.ui.components.SettingsRow
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.icons.FlowVoiceIcons
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.screens.diagnostics.DiagnosticsLog
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class SettingsUiState(
    val keyConfigured: Boolean,
    val maskedKey: String?,
    val keyDraft: String,
    val keyDraftVisible: Boolean,
    val validatingKey: Boolean,
    val keyMessage: String?,
    val modelLabel: String,
    val languageLabel: String,
    val accessibilityActive: Boolean,
    val overlayRunning: Boolean,
    val overlayMessage: String?,
    val proofreadingEnabled: Boolean,
    val accountEmail: String?,
    val googleWebClientId: String,
    val signingIn: Boolean,
    val syncing: Boolean,
    val accountMessage: String?
)

data class SettingsActions(
    val onKeyDraftChange: (String) -> Unit,
    val onToggleKeyDraftVisibility: () -> Unit,
    val onSaveKey: () -> Unit,
    val onOverlayToggle: () -> Unit,
    val onProofreadingChange: (Boolean) -> Unit,
    val onOpenDiagnostics: () -> Unit,
    val onClientIdChange: (String) -> Unit,
    val onSignIn: () -> Unit,
    val onSignOut: () -> Unit,
    val onSync: () -> Unit
)

@Composable
fun SettingsRoute(onOpenDiagnostics: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secretStore = rememberKoin<SecretStore>()
    val keyValidator = rememberKoin<OpenRouterKeyValidator>()
    val config = rememberKoin<OpenRouterConfig>()
    val preferencesStore = rememberKoin<PreferencesStore>()
    val authGateway = rememberKoin<AuthGateway>()
    val syncEngine = rememberKoin<SyncEngine>()
    val diagnosticsLog = rememberKoin<DiagnosticsLog>()

    var maskedKey by remember { mutableStateOf(KeyMask.mask(secretStore.readOpenRouterKey())) }
    var keyDraft by remember { mutableStateOf("") }
    var keyDraftVisible by remember { mutableStateOf(false) }
    var validatingKey by remember { mutableStateOf(false) }
    var keyMessage by remember { mutableStateOf<String?>(null) }
    var preferences by remember { mutableStateOf(preferencesStore.read()) }
    var accountEmail by remember { mutableStateOf(authGateway.currentUser()?.email) }
    var accessibilityActive by remember { mutableStateOf(FlowVoiceAccessibilityService.isRunning) }
    var overlayMessage by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var accountMessage by remember { mutableStateOf<String?>(null) }
    val overlayRunning by FlowVoiceOverlayService.runningFlow.collectAsState()

    LifecycleResumeEffect(Unit) {
        accessibilityActive = FlowVoiceAccessibilityService.isRunning
        maskedKey = KeyMask.mask(secretStore.readOpenRouterKey())
        preferences = preferencesStore.read()
        accountEmail = authGateway.currentUser()?.email
        onPauseOrDispose { }
    }

    fun applyOverlayAction(action: OverlayToggleAction, requestPermissions: (Array<String>) -> Unit) {
        val message = when (action) {
            OverlayToggleAction.Stop -> {
                context.startService(
                    Intent(context, FlowVoiceOverlayService::class.java).setAction(FlowVoiceOverlayService.ACTION_STOP)
                )
                "Botão flutuante desligado."
            }
            OverlayToggleAction.Unsupported -> "O botão flutuante requer Android 8 ou superior."
            OverlayToggleAction.RequestOverlayPermission -> {
                openOverlayPermissionSettings(context)
                "Autorize “sobrepor a outros apps” e ligue de novo."
            }
            is OverlayToggleAction.RequestPermissions -> {
                requestPermissions(action.permissions.toTypedArray())
                null
            }
            OverlayToggleAction.MicrophoneDenied -> "O botão flutuante precisa da permissão de microfone."
            OverlayToggleAction.Start -> {
                ContextCompat.startForegroundService(context, Intent(context, FlowVoiceOverlayService::class.java))
                "Botão flutuante ligado."
            }
        }
        overlayMessage = message
        message?.let(diagnosticsLog::add)
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        applyOverlayAction(
            overlayDecision(context, FlowVoiceOverlayService.running, permissionsRequested = true)
        ) { }
    }

    fun saveKey() {
        val candidate = keyDraft
        if (candidate.isBlank() || validatingKey) return
        validatingKey = true
        keyMessage = "Validando chave…"
        diagnosticsLog.add("Validando chave OpenRouter.")
        scope.launch {
            val message = when (keyValidator.validate(candidate)) {
                KeyValidationResult.Valid -> try {
                    secretStore.writeOpenRouterKey(candidate)
                    keyDraft = ""
                    keyDraftVisible = false
                    maskedKey = KeyMask.mask(secretStore.readOpenRouterKey())
                    "Chave validada e guardada cifrada."
                } catch (_: SecretStoreUnavailableException) {
                    "Cofre da chave indisponível neste aparelho; a chave não foi salva."
                }
                KeyValidationResult.InvalidFormat -> "Formato inválido: a chave começa com sk- e não tem espaços."
                KeyValidationResult.Rejected -> "A OpenRouter recusou esta chave."
                KeyValidationResult.Unavailable -> "Não foi possível validar agora (rede ou serviço)."
            }
            keyMessage = message
            diagnosticsLog.add(message)
            validatingKey = false
        }
    }

    fun signIn() {
        val clientId = preferences.googleWebClientId.trim()
        val activity = context.findActivity()
        if (signingIn) return
        if (clientId.isBlank() || activity == null) {
            accountMessage = "Informe o Google Web Client ID para entrar."
            return
        }
        signingIn = true
        scope.launch {
            val message = try {
                authGateway.signIn(GoogleSignInHelper.signIn(activity, clientId))
                accountEmail = authGateway.currentUser()?.email
                "Google autenticado."
            } catch (error: CancellationException) {
                throw error
            } catch (_: GetCredentialCancellationException) {
                "Login cancelado."
            } catch (_: NoCredentialException) {
                "Nenhuma conta Google disponível neste aparelho."
            } catch (error: Exception) {
                "Falha no Google Sign-In (${error::class.simpleName})."
            }
            accountMessage = message
            diagnosticsLog.add(message)
            signingIn = false
        }
    }

    fun sync() {
        if (syncing) return
        syncing = true
        scope.launch {
            val message = try {
                val result = syncEngine.sync()
                if (result.pushed) {
                    "Sincronizado: ${result.mergedNotes} notas · ${result.mergedTerms} termos (servidor remoto ainda local)."
                } else {
                    "Entre com o Google para sincronizar."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                "Falha na sincronização (${error::class.simpleName})."
            }
            accountMessage = message
            diagnosticsLog.add(message)
            syncing = false
        }
    }

    val state = SettingsUiState(
        keyConfigured = maskedKey != null,
        maskedKey = maskedKey,
        keyDraft = keyDraft,
        keyDraftVisible = keyDraftVisible,
        validatingKey = validatingKey,
        keyMessage = keyMessage,
        modelLabel = config.model.substringAfterLast('/'),
        languageLabel = dictationLanguageLabel(config.language),
        accessibilityActive = accessibilityActive,
        overlayRunning = overlayRunning,
        overlayMessage = overlayMessage,
        proofreadingEnabled = preferences.proofreadingEnabled,
        accountEmail = accountEmail,
        googleWebClientId = preferences.googleWebClientId,
        signingIn = signingIn,
        syncing = syncing,
        accountMessage = accountMessage
    )
    val actions = SettingsActions(
        onKeyDraftChange = { keyDraft = it },
        onToggleKeyDraftVisibility = { keyDraftVisible = !keyDraftVisible },
        onSaveKey = ::saveKey,
        onOverlayToggle = {
            applyOverlayAction(
                overlayDecision(context, overlayRunning, permissionsRequested = false)
            ) { overlayPermissionLauncher.launch(it) }
        },
        onProofreadingChange = { enabled ->
            preferencesStore.write(preferences.copy(proofreadingEnabled = enabled))
            preferences = preferencesStore.read()
        },
        onOpenDiagnostics = onOpenDiagnostics,
        onClientIdChange = { clientId ->
            preferencesStore.write(preferences.copy(googleWebClientId = clientId.trim()))
            preferences = preferencesStore.read()
        },
        onSignIn = ::signIn,
        onSignOut = {
            authGateway.signOut()
            accountEmail = null
            accountMessage = "Sessão Google encerrada."
            diagnosticsLog.add("Sessão Google encerrada.")
        },
        onSync = ::sync
    )
    SettingsContent(state = state, actions = actions, modifier = modifier)
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Ajustes",
            style = typography.screenTitle,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KeyCard(state, actions)
            SettingsCard {
                SettingsRow(
                    label = "Modelo de transcrição",
                    hint = "Padrão escolhido no benchmark F05",
                    value = state.modelLabel
                )
                FvDivider()
                SettingsRow(
                    label = "Idioma do ditado",
                    hint = "Português brasileiro",
                    value = state.languageLabel
                )
                FvDivider()
                SettingsRow(
                    label = "Serviço de acessibilidade",
                    hint = if (state.accessibilityActive) "Rota commitText ativa" else "Desligado: toque para ver o diagnóstico",
                    value = if (state.accessibilityActive) "ativo" else "inativo",
                    onClick = actions.onOpenDiagnostics
                )
                FvDivider()
                SettingsRow(
                    label = "Botão flutuante",
                    hint = state.overlayMessage ?: "Dita no app aberto sem trocar o teclado"
                ) {
                    FvToggle(checked = state.overlayRunning, onCheckedChange = { actions.onOverlayToggle() })
                }
                FvDivider()
                SettingsRow(
                    label = "Revisão por IA",
                    hint = "Pontuação e ortografia antes de inserir"
                ) {
                    FvToggle(checked = state.proofreadingEnabled, onCheckedChange = actions.onProofreadingChange)
                }
            }
            AccountCard(state, actions)
            DiagnosticsLinkCard(actions.onOpenDiagnostics)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun KeyCard(state: SettingsUiState, actions: SettingsActions) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    FvCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoLabel("Chave OpenRouter")
            Text(
                text = if (state.keyConfigured) "CONFIGURADA" else "AUSENTE",
                style = typography.monoStatus,
                color = if (state.keyConfigured) colors.accentText else colors.textTertiary
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FvDarkField(
                value = state.keyDraft,
                onValueChange = actions.onKeyDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = state.maskedKey ?: "sk-or-v1-…",
                visualTransformation = if (state.keyDraftVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation('•')
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSaveKey() }),
                enabled = !state.validatingKey
            )
            FvFieldButton(
                text = if (state.keyDraftVisible) "ocultar" else "mostrar",
                onClick = actions.onToggleKeyDraftVisibility,
                enabled = state.keyDraft.isNotEmpty()
            )
        }
        if (state.keyDraft.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = if (state.validatingKey) "Validando…" else "Validar e salvar",
                onClick = actions.onSaveKey,
                enabled = !state.validatingKey,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = state.keyMessage ?: "Guardada cifrada no aparelho. Nunca sincronizada.",
            style = typography.bodySmall,
            color = colors.textTertiary
        )
    }
}

@Composable
private fun AccountCard(state: SettingsUiState, actions: SettingsActions) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val signedIn = state.accountEmail != null
    FvCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.avatar),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.accountEmail?.firstOrNull()?.uppercase() ?: "?",
                    style = typography.rowLabel.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.chipContent
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.accountEmail ?: "Não autenticado",
                    style = typography.rowLabel,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.accountMessage ?: if (signedIn) {
                        "Sincronização ainda sem servidor remoto"
                    } else {
                        "Entre com o Google para sincronizar notas e dicionário"
                    },
                    style = typography.bodySmall,
                    color = colors.textTertiary
                )
            }
            if (signedIn) {
                FvTextAction(text = "sair", onClick = actions.onSignOut, activeColor = colors.destructiveText)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (signedIn) {
            PillButton(
                text = if (state.syncing) "Sincronizando…" else "Sincronizar",
                onClick = actions.onSync,
                variant = PillButtonVariant.Outline,
                height = 34.dp,
                enabled = !state.syncing,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            FvDarkField(
                value = state.googleWebClientId,
                onValueChange = actions.onClientIdChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Google Web Client ID",
                textStyle = typography.monoValue,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)
            )
            Spacer(Modifier.height(8.dp))
            PillButton(
                text = if (state.signingIn) "Entrando…" else "Entrar com Google",
                onClick = actions.onSignIn,
                enabled = state.googleWebClientId.isNotBlank() && !state.signingIn,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DiagnosticsLinkCard(onOpenDiagnostics: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    FvCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenDiagnostics,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Diagnóstico técnico", style = typography.rowLabel, color = colors.textPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Rota de inserção, permissões e log do serviço",
                    style = typography.bodySmall,
                    color = colors.textTertiary
                )
            }
            Icon(
                imageVector = FlowVoiceIcons.ChevronRight,
                contentDescription = null,
                tint = colors.iconMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

internal fun dictationLanguageLabel(language: String): String =
    if (language.equals("pt", ignoreCase = true) || language.equals("pt-BR", ignoreCase = true)) "pt-BR" else language

private fun overlayDecision(context: Context, running: Boolean, permissionsRequested: Boolean): OverlayToggleAction =
    OverlayToggle.decide(
        running = running,
        sdkInt = Build.VERSION.SDK_INT,
        canDrawOverlays = Settings.canDrawOverlays(context),
        microphoneGranted = context.isGranted(Manifest.permission.RECORD_AUDIO),
        notificationsGranted = Build.VERSION.SDK_INT < OverlayToggle.SDK_POST_NOTIFICATIONS ||
            context.isGranted(OverlayToggle.POST_NOTIFICATIONS),
        permissionsRequested = permissionsRequested
    )

private fun openOverlayPermissionSettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview
@Composable
private fun SettingsContentPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        SettingsContent(
            state = SettingsUiState(
                keyConfigured = true,
                maskedKey = "sk-or-v1-••••4f9c",
                keyDraft = "",
                keyDraftVisible = false,
                validatingKey = false,
                keyMessage = null,
                modelLabel = "gpt-4o-mini-transcribe",
                languageLabel = "pt-BR",
                accessibilityActive = true,
                overlayRunning = false,
                overlayMessage = null,
                proofreadingEnabled = true,
                accountEmail = "rafael@gmail.com",
                googleWebClientId = "",
                signingIn = false,
                syncing = false,
                accountMessage = null
            ),
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        )
    }
}
