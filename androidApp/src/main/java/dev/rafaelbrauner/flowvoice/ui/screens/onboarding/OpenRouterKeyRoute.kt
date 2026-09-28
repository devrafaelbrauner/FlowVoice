package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.net.toUri
import dev.rafaelbrauner.flowvoice.localasr.LocalModelInstaller
import dev.rafaelbrauner.flowvoice.localasr.LocalModelState
import dev.rafaelbrauner.flowvoice.shared.localasr.NemotronModel
import dev.rafaelbrauner.flowvoice.ui.components.FvCard
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.components.PillButtonVariant
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.screens.settings.FvTextAction
import dev.rafaelbrauner.flowvoice.ui.screens.settings.OpenRouterKeyInput
import dev.rafaelbrauner.flowvoice.ui.screens.settings.rememberOpenRouterKeyEntry
import dev.rafaelbrauner.flowvoice.ui.shell.startActivitySafely
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import java.util.Locale

object OpenRouterKeyCopy {
    const val KEYS_URL = "https://openrouter.ai/keys"
    const val TITLE = "Sua chave OpenRouter"
    const val INTRO = "Na nuvem, quem transforma sua voz em texto é a OpenRouter. Você cria sua " +
        "própria conta lá e gera uma chave; o FlowVoice usa essa chave para pedir cada transcrição."
    val STEPS = listOf(
        "Crie uma conta na OpenRouter. Ela precisa de créditos pré-pagos, comprados no cartão: a compra " +
            "mínima é de ${DictationCostEstimate.minPurchaseLabel()}.",
        "Na página de chaves, crie uma chave nova e copie. Ela começa com sk-or-.",
        "Cole a chave aqui embaixo e toque em Validar e salvar."
    )
    const val OPEN_KEYS = "Abrir openrouter.ai/keys"
    const val NO_BROWSER = "Nenhum navegador abriu. Acesse openrouter.ai/keys em outro aparelho."
    const val KEY_HINT = "Guardada cifrada neste celular. Só vai à OpenRouter, para autorizar cada " +
        "transcrição; nunca é sincronizada."

    fun costDetail(): String =
        "Conta com o modelo padrão (gpt-transcribe), a taxa da OpenRouter, o IOF e o dólar a R$ " +
            String.format(Locale.forLanguageTag("pt-BR"), "%.2f", DictationCostEstimate.USD_TO_BRL) +
            ". O valor real muda com o câmbio e com o seu cartão."

    const val LOCAL_LABEL = "Ou use o motor no aparelho"
    // O botão é de uma linha só: no S26 o rótulo longo saía cortado ("funciona sem internet e ...").
    const val LOCAL_DOWNLOAD = "Baixar o modelo (475 MB)"
    const val LOCAL_DETAIL = "Funciona sem internet e sem custo: o áudio é transcrito no celular e não sai dele. " +
        "O modelo é baixado uma vez do " +
        "GitHub (k2-fsa/sherpa-onnx) e precisa de cerca de 1,2 GB livres durante a instalação. A revisão " +
        "por IA continua precisando da chave."
    const val LOCAL_VERIFYING = "Conferindo…"
    const val LOCAL_READY = "Pronto: o ditado usa o modelo no aparelho"
    const val LOCAL_CONTINUE = "Continuar"
    const val LOCAL_CANCEL = "Cancelar"
    const val LOCAL_RETRY = "Tentar de novo"

    fun downloadingLabel(downloadedBytes: Long, totalBytes: Long): String {
        val total = totalBytes.takeIf { it > 0 } ?: NemotronModel.ARCHIVE_BYTES
        val percent = (downloadedBytes * 100 / total).coerceIn(0, 100)
        return "Baixando ${downloadedBytes / 1_000_000} MB de ${total / 1_000_000} MB · $percent%"
    }

    fun extractingLabel(progress: Float): String =
        "Extraindo… ${(progress * 100).toInt().coerceIn(0, 100)}%"
}

@Composable
fun OpenRouterKeyRoute(onBack: () -> Unit, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entry = rememberOpenRouterKeyEntry()
    val installer = rememberKoin<LocalModelInstaller>()
    val modelState by installer.state.collectAsState()
    var browserMissing by remember { mutableStateOf(false) }
    OpenRouterKeyScreen(
        draft = entry.draft,
        draftVisible = entry.draftVisible,
        validating = entry.validating,
        maskedKey = entry.maskedKey,
        message = entry.message,
        browserMissing = browserMissing,
        onBack = onBack,
        onOpenKeys = {
            val intent = Intent(Intent.ACTION_VIEW, OpenRouterKeyCopy.KEYS_URL.toUri())
                .addCategory(Intent.CATEGORY_BROWSABLE)
            browserMissing = !context.startActivitySafely(intent)
        },
        onDraftChange = { entry.draft = it },
        onToggleDraftVisibility = { entry.draftVisible = !entry.draftVisible },
        onSave = { entry.save(onSaved = onSaved) },
        modelState = modelState,
        onInstallModel = { installer.install(useWhenReady = true) },
        onCancelModel = installer::cancel,
        onModelReady = onSaved,
        modifier = modifier
    )
}

@Composable
internal fun OpenRouterKeyScreen(
    draft: String,
    draftVisible: Boolean,
    validating: Boolean,
    maskedKey: String?,
    message: String?,
    browserMissing: Boolean,
    onBack: () -> Unit,
    onOpenKeys: () -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleDraftVisibility: () -> Unit,
    onSave: () -> Unit,
    modelState: LocalModelState,
    onInstallModel: () -> Unit,
    onCancelModel: () -> Unit,
    onModelReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = modifier
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
    ) {
        FvTextAction(text = "← voltar", onClick = onBack)
        Spacer(Modifier.height(8.dp))
        MonoLabel("Passo 3 · transcrição")
        Spacer(Modifier.height(14.dp))
        Text(
            text = OpenRouterKeyCopy.TITLE,
            style = typography.screenTitle.copy(lineHeight = 1.1.em),
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = OpenRouterKeyCopy.INTRO,
            style = typography.body.copy(lineHeight = 1.55.em),
            color = colors.textMuted
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OpenRouterKeyCopy.STEPS.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textMuted
                    )
                    Text(text = step, style = typography.bodyMedium, color = colors.textMuted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton(
            text = OpenRouterKeyCopy.OPEN_KEYS,
            onClick = onOpenKeys,
            height = 44.dp,
            modifier = Modifier.fillMaxWidth()
        )
        if (browserMissing) {
            Spacer(Modifier.height(6.dp))
            Text(text = OpenRouterKeyCopy.NO_BROWSER, style = typography.bodySmall, color = colors.destructiveText)
        }
        Spacer(Modifier.height(14.dp))
        FvCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            MonoLabel("Quanto custa")
            Spacer(Modifier.height(8.dp))
            Text(
                text = DictationCostEstimate.perHourLabel(),
                style = typography.itemTitle,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(text = OpenRouterKeyCopy.costDetail(), style = typography.bodySmall, color = colors.textTertiary)
        }
        Spacer(Modifier.height(14.dp))
        FvCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            MonoLabel("Chave OpenRouter")
            Spacer(Modifier.height(10.dp))
            OpenRouterKeyInput(
                draft = draft,
                draftVisible = draftVisible,
                validating = validating,
                maskedKey = maskedKey,
                message = message,
                idleHint = OpenRouterKeyCopy.KEY_HINT,
                onDraftChange = onDraftChange,
                onToggleDraftVisibility = onToggleDraftVisibility,
                onSave = onSave,
                alwaysShowSave = true
            )
        }
        Spacer(Modifier.height(22.dp))
        LocalModelCard(
            state = modelState,
            onInstall = onInstallModel,
            onCancel = onCancelModel,
            onReady = onModelReady
        )
    }
}

// Alternativa sem chave: o custo acima vale só para a nuvem, então fica fora deste bloco.
@Composable
private fun LocalModelCard(
    state: LocalModelState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onReady: () -> Unit
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    FvCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        MonoLabel(OpenRouterKeyCopy.LOCAL_LABEL)
        Spacer(Modifier.height(8.dp))
        Text(text = OpenRouterKeyCopy.LOCAL_DETAIL, style = typography.bodySmall, color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        val progress = when (state) {
            is LocalModelState.Downloading -> OpenRouterKeyCopy.downloadingLabel(state.downloadedBytes, state.totalBytes)
            LocalModelState.Verifying -> OpenRouterKeyCopy.LOCAL_VERIFYING
            is LocalModelState.Extracting -> OpenRouterKeyCopy.extractingLabel(state.progress)
            else -> null
        }
        when {
            state is LocalModelState.Installed -> {
                Text(text = OpenRouterKeyCopy.LOCAL_READY, style = typography.itemTitle, color = colors.accentText)
                Spacer(Modifier.height(10.dp))
                PillButton(
                    text = OpenRouterKeyCopy.LOCAL_CONTINUE,
                    onClick = onReady,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            progress != null -> {
                Text(text = progress, style = typography.bodyMedium, color = colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                PillButton(
                    text = OpenRouterKeyCopy.LOCAL_CANCEL,
                    onClick = onCancel,
                    variant = PillButtonVariant.Outline,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                if (state is LocalModelState.Failed) {
                    Text(text = state.message, style = typography.bodySmall, color = colors.destructiveText)
                    Spacer(Modifier.height(10.dp))
                }
                PillButton(
                    text = if (state is LocalModelState.Failed) {
                        OpenRouterKeyCopy.LOCAL_RETRY
                    } else {
                        OpenRouterKeyCopy.LOCAL_DOWNLOAD
                    },
                    onClick = onInstall,
                    variant = PillButtonVariant.Outline,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(heightDp = 1100)
@Composable
private fun OpenRouterKeyScreenPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        OpenRouterKeyScreen(
            draft = "",
            draftVisible = false,
            validating = false,
            maskedKey = null,
            message = null,
            browserMissing = dark,
            onBack = {},
            onOpenKeys = {},
            onDraftChange = {},
            onToggleDraftVisibility = {},
            onSave = {},
            modelState = if (dark) {
                LocalModelState.Downloading(120_000_000, NemotronModel.ARCHIVE_BYTES)
            } else {
                LocalModelState.NotInstalled
            },
            onInstallModel = {},
            onCancelModel = {},
            onModelReady = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
