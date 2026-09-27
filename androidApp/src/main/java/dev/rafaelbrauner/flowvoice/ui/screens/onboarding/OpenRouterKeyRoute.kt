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
import dev.rafaelbrauner.flowvoice.ui.components.FvCard
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.screens.settings.FvTextAction
import dev.rafaelbrauner.flowvoice.ui.screens.settings.OpenRouterKeyInput
import dev.rafaelbrauner.flowvoice.ui.screens.settings.rememberOpenRouterKeyEntry
import dev.rafaelbrauner.flowvoice.ui.shell.startActivitySafely
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme
import java.util.Locale

object OpenRouterKeyCopy {
    const val KEYS_URL = "https://openrouter.ai/keys"
    const val TITLE = "Sua chave OpenRouter"
    const val INTRO = "A OpenRouter é o serviço na nuvem que transforma sua voz em texto. Você cria sua " +
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
}

@Composable
fun OpenRouterKeyRoute(onBack: () -> Unit, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entry = rememberOpenRouterKeyEntry()
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
        MonoLabel("Passo 3 · chave")
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
            modifier = Modifier.fillMaxSize()
        )
    }
}
