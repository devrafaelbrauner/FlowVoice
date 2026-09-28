package dev.rafaelbrauner.flowvoice.shared.model

import dev.rafaelbrauner.flowvoice.shared.persist.MapRawKeyValue
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.shared.prefs.GuardedPreferencesStore
import kotlin.test.Test
import kotlin.test.assertEquals

// "Formatação" em Ajustes: a escolha grava no JSON das preferências e volta igual ao reabrir o app.
class CloudModelsTest {
    @Test
    fun withoutAChoiceTheFinalPassFormatsWithTheMeasuredDefault() {
        assertEquals(
            FormattingChoice.Llm(AppPreferences.DEFAULT_PROOFREADING_MODEL),
            CloudModels.formatting(AppPreferences())
        )
    }

    @Test
    fun eachChoiceSurvivesTheStoredPreferences() {
        val store = GuardedPreferencesStore(MapRawKeyValue(), "prefs_json")
        val choices = listOf(
            FormattingChoice.OneStep("google/gemini-3.8-flash"),
            FormattingChoice.None,
            FormattingChoice.Llm("openai/gpt-4.1-mini")
        )

        choices.forEach { choice ->
            store.write(CloudModels.withFormatting(store.read(), choice))

            assertEquals(choice, CloudModels.formatting(store.read()))
        }
    }

    // Quem escolheu um modelo de passo único e depois "sem formatação" reencontra o mesmo modelo ao voltar ao
    // passo único; o modelo de formatação também não se perde.
    @Test
    fun switchingModesKeepsTheModelOfTheOtherMode() {
        val oneStep = CloudModels.withFormatting(AppPreferences(), FormattingChoice.OneStep("openai/gpt-audio"))
        val llm = CloudModels.withFormatting(oneStep, FormattingChoice.Llm("openai/gpt-4.1-mini"))
        val none = CloudModels.withFormatting(llm, FormattingChoice.None)

        assertEquals("openai/gpt-audio", none.oneStepModel)
        assertEquals("openai/gpt-4.1-mini", none.proofreadingModel)
    }

    // Preferências gravadas antes do seletor não têm `formattingMode`: vale a formatação com o modelo que já
    // estava gravado.
    @Test
    fun preferencesSavedBeforeTheSelectorKeepTheirFormattingModel() {
        val store = GuardedPreferencesStore(
            MapRawKeyValue("prefs_json" to """{"proofreadingEnabled":true,"proofreadingModel":"openai/gpt-4o-mini"}"""),
            "prefs_json"
        )

        assertEquals(FormattingChoice.Llm("openai/gpt-4o-mini"), CloudModels.formatting(store.read()))
    }

    @Test
    fun aOneStepChoiceWithoutAModelUsesTheMeasuredOneStepDefault() {
        val prefs = CloudModels.withFormatting(AppPreferences(), FormattingChoice.OneStep(""))

        assertEquals(FormattingChoice.OneStep(CloudModels.DEFAULT_ONE_STEP_MODEL), CloudModels.formatting(prefs))
    }

    @Test
    fun theMeasuredTranscriptionModelsComeFirstAndNoCatalogModelIsLost() {
        val measured = CloudModels.transcription.map { it.id }
        val catalog = listOf("zeta/novo-modelo") + measured.reversed() + listOf("alfa/outro")

        val ordered = CloudModels.orderedTranscription(catalog)

        assertEquals(measured + listOf("zeta/novo-modelo", "alfa/outro"), ordered)
    }
}
