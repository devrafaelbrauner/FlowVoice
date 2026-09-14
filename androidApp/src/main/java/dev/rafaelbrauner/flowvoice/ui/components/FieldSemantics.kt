package dev.rafaelbrauner.flowvoice.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

// O Compose não expõe hint para o TalkBack: um contentDescription fixo num campo editável
// substituiria o texto digitado. O rótulo vale só com o campo vazio, como um hint.
object FieldSemantics {
    fun description(label: String?, value: String): String? =
        label.takeIf { value.isEmpty() }
}

fun Modifier.fieldDescription(label: String?, value: String): Modifier =
    semantics { FieldSemantics.description(label, value)?.let { contentDescription = it } }
