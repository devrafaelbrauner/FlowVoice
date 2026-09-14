package dev.rafaelbrauner.flowvoice.ui.screens.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.rafaelbrauner.flowvoice.shared.dictionary.PersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.PillButton
import dev.rafaelbrauner.flowvoice.ui.components.PillButtonVariant
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.components.fieldDescription
import dev.rafaelbrauner.flowvoice.ui.rememberKoin
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceRadius
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

@Composable
fun DictionaryRoute(modifier: Modifier = Modifier) {
    val dictionary = rememberKoin<PersonalDictionary>()
    val pipeline = rememberKoin<DictationPipeline>()
    val state = remember(dictionary) { DictionaryScreenState(dictionary) }
    val status by pipeline.status.collectAsState()

    LaunchedEffect(status) { state.refresh() }

    DictionaryScreen(
        pending = state.pending,
        approved = state.approved,
        draft = state.draft,
        onApprove = state::approve,
        onDiscard = state::discard,
        onDraftChange = state::updateDraft,
        onAddDraft = { state.addDraft() },
        modifier = modifier
    )
}

@Composable
internal fun DictionaryScreen(
    pending: List<String>,
    approved: List<String>,
    draft: String,
    onApprove: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onAddDraft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
            MonoLabel(
                text = DictionaryScreenState.pendingLabel(pending.size),
                style = typography.monoLabel.copy(letterSpacing = 0.16.em)
            )
            Text(
                text = "Dicionário",
                style = typography.screenTitle,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "Termos que o modelo errou e você corrigiu. Aprovados entram na revisão das próximas transcrições.",
                style = typography.bodyMedium.copy(lineHeight = 1.5.em),
                color = colors.textMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            pending.forEach { term ->
                PendingTermCard(
                    term = term,
                    onApprove = { onApprove(term) },
                    onDiscard = { onDiscard(term) }
                )
            }
        }
        MonoLabel(
            text = "Aprovados",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NewTermRow(draft = draft, onDraftChange = onDraftChange, onAdd = onAddDraft)
            if (approved.isEmpty()) {
                Text(
                    text = "Nenhum termo aprovado ainda.",
                    style = typography.bodyMedium,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
            approved.forEach { term -> ApprovedTermRow(term) }
        }
    }
}

@Composable
private fun PendingTermCard(term: String, onApprove: () -> Unit, onDiscard: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val shape = RoundedCornerShape(FlowVoiceRadius.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .padding(14.dp)
    ) {
        Text(
            text = term,
            style = typography.monoKey.copy(lineHeight = 1.3.em),
            color = colors.accentText
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillButton(
                text = "Aprovar",
                onClick = onApprove,
                modifier = Modifier.weight(1f)
            )
            PillButton(
                text = "Descartar",
                onClick = onDiscard,
                variant = PillButtonVariant.Outline,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ApprovedTermRow(term: String) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val shape = RoundedCornerShape(FlowVoiceRadius.chip)
    Text(
        text = term,
        style = typography.monoKey.copy(lineHeight = 1.em),
        color = colors.chipContent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.chip)
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun NewTermRow(draft: String, onDraftChange: (String) -> Unit, onAdd: () -> Unit) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    val shape = RoundedCornerShape(FlowVoiceRadius.field)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(shape)
                .background(colors.field)
                .border(1.dp, colors.hairlineInput, shape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = "Novo termo",
                    style = typography.monoKey,
                    color = colors.textTertiary,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = typography.monoKey.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .fieldDescription("Novo termo", draft)
            )
        }
        PillButton(
            text = "Adicionar",
            onClick = onAdd,
            variant = PillButtonVariant.Outline,
            enabled = draft.isNotBlank()
        )
    }
}

@Preview(widthDp = 412, heightDp = 900)
@Composable
private fun DictionaryScreenPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        DictionaryScreen(
            pending = listOf("Brauner", "commitText", "S26 Ultra"),
            approved = listOf("OpenRouter", "Kotlin", "SQLDelight", "Supabase"),
            draft = "",
            onApprove = {},
            onDiscard = {},
            onDraftChange = {},
            onAddDraft = {}
        )
    }
}

@Preview(widthDp = 412, heightDp = 600)
@Composable
private fun DictionaryScreenEmptyPreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        DictionaryScreen(
            pending = emptyList(),
            approved = emptyList(),
            draft = "",
            onApprove = {},
            onDiscard = {},
            onDraftChange = {},
            onAddDraft = {}
        )
    }
}
