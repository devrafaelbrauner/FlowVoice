package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.ui.components.FvCard
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.components.Wordmark
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.screens.settings.FvFieldButton
import dev.rafaelbrauner.flowvoice.ui.shell.startActivitySafely
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import dev.rafaelbrauner.flowvoice.ui.theme.PillShape

@Composable
fun OnboardingRoute(
    onProgress: (OnboardingProgress) -> Unit,
    onLeave: (progress: OnboardingProgress, skipped: Boolean) -> Unit,
    onOpenAccessibilityDisclosure: () -> Unit,
    onOpenKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val secretStore = rememberKoin<SecretStore>()
    val installer = remember { installerPackage(context) }
    var progress by remember { mutableStateOf(readProgress(context, secretStore)) }
    val latestOnProgress by rememberUpdatedState(onProgress)

    LifecycleResumeEffect(Unit) {
        progress = readProgress(context, secretStore)
        onPauseOrDispose { }
    }

    LaunchedEffect(progress) { latestOnProgress(progress) }

    val microphoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        progress = readProgress(context, secretStore)
    }

    OnboardingScreen(
        progress = progress,
        showRestrictedSettingsHint = RestrictedSettingsHint.shouldShow(
            sdkInt = Build.VERSION.SDK_INT,
            accessibilityActive = progress.accessibility,
            installer = installer
        ),
        onAccessibility = onOpenAccessibilityDisclosure,
        onOpenAppDetails = {
            context.startActivitySafely(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            )
        },
        onMicrophone = {
            if (!progress.microphone) microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onKey = onOpenKey,
        onStart = { onLeave(progress, false) },
        onSkip = { onLeave(progress, true) },
        modifier = modifier
    )
}

private fun readProgress(context: Context, secretStore: SecretStore) = OnboardingProgress(
    accessibility = FlowVoiceAccessibilityService.isRunning,
    microphone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED,
    key = secretStore.readOpenRouterKey() != null
)

private fun installerPackage(context: Context): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    } else {
        null
    }

@Composable
internal fun OnboardingScreen(
    progress: OnboardingProgress,
    onAccessibility: () -> Unit,
    onMicrophone: () -> Unit,
    onKey: () -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    showRestrictedSettingsHint: Boolean = false,
    onOpenAppDetails: () -> Unit = {}
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val next = progress.nextPending
    Column(
        modifier = modifier
            .background(colors.background)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Wordmark()
            Spacer(Modifier.height(26.dp))
            Text(
                text = "Três passos e você já está ditando.",
                style = typography.screenTitle.copy(lineHeight = 1.1.em),
                color = colors.textPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "O FlowVoice não substitui seu teclado. Ele escreve no campo que já está aberto.",
                style = typography.body.copy(lineHeight = 1.55.em),
                color = colors.textMuted
            )
            Spacer(Modifier.height(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OnboardingStepCard(
                    number = 1,
                    title = "Serviço de acessibilidade",
                    description = "É o que permite escrever no campo ativo sem trocar seu teclado.",
                    done = progress.accessibility,
                    next = next == OnboardingStep.Accessibility,
                    onClick = onAccessibility
                )
                if (showRestrictedSettingsHint) {
                    RestrictedSettingsHintCard(onOpenAppDetails = onOpenAppDetails)
                }
                OnboardingStepCard(
                    number = 2,
                    title = "Microfone",
                    description = "Grava só durante o ditado: toque para começar, toque de novo para parar.",
                    done = progress.microphone,
                    next = next == OnboardingStep.Microphone,
                    onClick = onMicrophone
                )
                OnboardingStepCard(
                    number = 3,
                    title = "Chave OpenRouter",
                    description = "Guardada cifrada neste celular. Só vai à OpenRouter, para autorizar cada " +
                        "transcrição; nunca é sincronizada.",
                    done = progress.key,
                    next = next == OnboardingStep.Key,
                    onClick = onKey
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        OnboardingCta(
            label = progress.primaryLabel,
            onClick = when (next) {
                null -> onStart
                OnboardingStep.Accessibility -> onAccessibility
                OnboardingStep.Microphone -> onMicrophone
                OnboardingStep.Key -> onKey
            }
        )
        if (next != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FlowVoiceSpacing.minTouchTarget)
                    .clickable(role = Role.Button, onClick = onSkip),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = OnboardingProgress.CTA_SKIP,
                    style = typography.buttonSecondary,
                    color = colors.textMuted
                )
            }
            Text(
                text = "O que faltar continua no Início e em Ajustes.",
                style = typography.bodySmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RestrictedSettingsHintCard(onOpenAppDetails: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    FvCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
        Text(text = RestrictedSettingsHint.TITLE, style = typography.itemTitleSmall, color = colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RestrictedSettingsHint.STEPS.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = typography.bodySmall.copy(lineHeight = 1.5.em, fontWeight = FontWeight.SemiBold),
                        color = colors.textMuted
                    )
                    Text(
                        text = step,
                        style = typography.bodySmall.copy(lineHeight = 1.5.em),
                        color = colors.textMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FvFieldButton(
            text = RestrictedSettingsHint.ACTION,
            onClick = onOpenAppDetails,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OnboardingStepCard(
    number: Int,
    title: String,
    description: String,
    done: Boolean,
    next: Boolean,
    onClick: () -> Unit
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(FlowVoiceRadius.card)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(
                if (next) 2.dp else 1.dp,
                if (done || next || pressed) colors.accentBorder else colors.hairlineInput,
                shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(FlowVoiceRadius.chip))
                .background(if (done) colors.accent else colors.badgeInactive),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = typography.monoStatus.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = if (done) colors.onAccent else colors.badgeInactiveContent
            )
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = typography.itemTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(text = description, style = typography.bodyMedium, color = colors.textMuted)
        }
        Text(
            text = when {
                done -> "ok"
                next -> "próximo"
                else -> "tocar"
            },
            style = typography.monoStatus,
            color = if (done || next) colors.accentText else colors.textTertiary
        )
    }
}

@Composable
internal fun OnboardingCta(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FlowVoiceTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(PillShape)
            .background(if (pressed) colors.primaryButtonContainerPressed else colors.primaryButtonContainer)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = FlowVoiceTheme.typography.buttonLarge, color = colors.primaryButtonContent)
    }
}

@Preview(heightDp = 760)
@Composable
private fun OnboardingScreenPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        OnboardingScreen(
            progress = OnboardingProgress(accessibility = true, microphone = true, key = false),
            onAccessibility = {},
            onMicrophone = {},
            onKey = {},
            onStart = {},
            onSkip = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(heightDp = 1000)
@Composable
private fun OnboardingRestrictedSettingsPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        OnboardingScreen(
            progress = OnboardingProgress(accessibility = false, microphone = true, key = true),
            onAccessibility = {},
            onMicrophone = {},
            onKey = {},
            onStart = {},
            onSkip = {},
            modifier = Modifier.fillMaxSize(),
            showRestrictedSettingsHint = true
        )
    }
}
