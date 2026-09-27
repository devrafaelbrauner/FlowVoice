package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.net.toUri
import dev.rafaelbrauner.flowvoice.ui.components.FvCard
import dev.rafaelbrauner.flowvoice.ui.components.MonoLabel
import dev.rafaelbrauner.flowvoice.ui.components.ThemePreviewParameter
import dev.rafaelbrauner.flowvoice.ui.screens.settings.FvTextAction
import dev.rafaelbrauner.flowvoice.ui.shell.AppLinks
import dev.rafaelbrauner.flowvoice.ui.shell.startActivitySafely
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceSpacing
import dev.rafaelbrauner.flowvoice.ui.theme.FlowVoiceTheme

// Divulgação em destaque exigida pelo Google Play para quem usa a AccessibilityService sem ser
// ferramenta de acessibilidade (sem isAccessibilityTool): dentro do app, antes do pedido do
// sistema, separada de outras divulgações, dizendo o que é acessado e como é usado, e só segue com
// um toque afirmativo. https://support.google.com/googleplay/android-developer/answer/10964491
// (e a política de Dados do usuário, answer/10144311). Cada frase confere com
// res/xml/accessibility_flowvoice.xml e FlowVoiceAccessibilityService: mudou o serviço, mude aqui.
object AccessibilityDisclosure {
    const val TITLE = "O que o FlowVoice vê"
    const val INTRO = "Para escrever no campo aberto sem trocar seu teclado, o FlowVoice usa o serviço de " +
        "acessibilidade do Android. Antes de ligar, veja o que ele acessa e para quê."
    val ACCESSES = listOf(
        "O nome do app que está na tela, a cada troca de janela. Serve só para o microfone do Início saber " +
            "para qual app voltar; o conteúdo da tela não é lido nesses avisos.",
        "No ditado, o campo de texto em que está o cursor: o FlowVoice escreve nele e lê o texto logo antes " +
            "do cursor para conferir e corrigir o que ele mesmo escreveu, inclusive na revisão por IA. Se a " +
            "escrita direta falhar, lê o texto do campo para inserir no ponto do cursor sem apagar nada. Só " +
            "lê o campo em que o ditado está escrevendo: se o foco for para outro app ou outro campo, nada é " +
            "lido até você tocar em Inserir aqui.",
        "A posição do campo, do cursor e do teclado na tela, para a bolha e a prévia não cobrirem o que " +
            "você escreve."
    )
    val LIMITS = listOf(
        "O texto lido do campo fica no celular: não é enviado nem guardado. O que vai para a internet é o " +
            "áudio, só enquanto você dita, para a OpenRouter transcrever (e, com a revisão por IA ligada, o " +
            "texto ditado).",
        "Não lê nem escreve em campos marcados como senha.",
        "Não toca em botões nem navega por você: só escreve e corrige o texto que você ditou e posiciona o cursor."
    )
    const val SYSTEM_WARNING = "Em seguida, o Android vai perguntar se o FlowVoice pode ter “controle total” " +
        "do aparelho e dizer que ele pode ler tudo o que aparece na tela. É o aviso padrão do Android para " +
        "qualquer serviço de acessibilidade, porque o sistema não sabe o que cada app faz. O FlowVoice usa " +
        "só o que está descrito acima."
    const val HOW_TO = "Na próxima tela, procure FlowVoice na lista de apps, ligue a chave e confirme."
    const val ACCEPT = "Entendi, abrir Acessibilidade"
    const val DECLINE = "Agora não"
    const val PRIVACY = "Política de privacidade"
}

@Composable
fun AccessibilityDisclosureRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AccessibilityDisclosureScreen(
        onAccept = {
            onBack()
            context.startActivitySafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onDecline = onBack,
        onOpenPrivacyPolicy = {
            context.startActivitySafely(Intent(Intent.ACTION_VIEW, AppLinks.PRIVACY_POLICY_URL.toUri()))
        },
        modifier = modifier
    )
}

@Composable
internal fun AccessibilityDisclosureScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
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
            MonoLabel("Passo 1 · acessibilidade")
            Spacer(Modifier.height(14.dp))
            Text(
                text = AccessibilityDisclosure.TITLE,
                style = typography.screenTitle.copy(lineHeight = 1.1.em),
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = AccessibilityDisclosure.INTRO,
                style = typography.body.copy(lineHeight = 1.55.em),
                color = colors.textMuted
            )
            Spacer(Modifier.height(20.dp))
            DisclosureSection(title = "O que ele acessa", items = AccessibilityDisclosure.ACCESSES)
            Spacer(Modifier.height(18.dp))
            DisclosureSection(title = "O que ele não faz", items = AccessibilityDisclosure.LIMITS)
            Spacer(Modifier.height(18.dp))
            FvCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
                Text(
                    text = AccessibilityDisclosure.SYSTEM_WARNING,
                    style = typography.bodyMedium,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = AccessibilityDisclosure.HOW_TO,
                    style = typography.bodyMedium,
                    color = colors.textMuted
                )
            }
            Spacer(Modifier.height(4.dp))
            FvTextAction(
                text = AccessibilityDisclosure.PRIVACY,
                onClick = onOpenPrivacyPolicy,
                color = colors.accentText
            )
            Spacer(Modifier.height(8.dp))
        }
        OnboardingCta(label = AccessibilityDisclosure.ACCEPT, onClick = onAccept)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlowVoiceSpacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onDecline),
            contentAlignment = Alignment.Center
        ) {
            Text(text = AccessibilityDisclosure.DECLINE, style = typography.buttonSecondary, color = colors.textMuted)
        }
    }
}

@Composable
private fun DisclosureSection(title: String, items: List<String>) {
    val colors = FlowVoiceTheme.colors
    val typography = FlowVoiceTheme.typography
    Text(
        text = title,
        style = typography.itemTitle,
        color = colors.textPrimary,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "•", style = typography.bodyMedium, color = colors.textMuted)
                Text(text = item, style = typography.bodyMedium, color = colors.textMuted)
            }
        }
    }
}

@Preview(heightDp = 1100)
@Composable
private fun AccessibilityDisclosurePreview(@PreviewParameter(ThemePreviewParameter::class) dark: Boolean) {
    FlowVoiceTheme(darkTheme = dark) {
        AccessibilityDisclosureScreen(onAccept = {}, onDecline = {}, onOpenPrivacyPolicy = {}, modifier = Modifier.fillMaxSize())
    }
}
