package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DictationCostEstimateTest {

    private val eps = 1e-6

    @Test
    fun overlapContextMakesShorterWindowsCostMore() {
        assertEquals(1.25, DictationCostEstimate.audioOverheadFactor(4.0), eps)
        assertEquals(1.5, DictationCostEstimate.audioOverheadFactor(2.0), eps)
    }

    @Test
    fun hourOfSpeechAtTheMeasuredPrice() {
        // 3600 s × US$ 0,00003/s = US$ 0,108 de áudio, mais o contexto repetido.
        assertEquals(0.135, DictationCostEstimate.transcriptionUsdPerHour(4.0), eps)
        assertEquals(0.162, DictationCostEstimate.transcriptionUsdPerHour(2.0), eps)
    }

    @Test
    fun minimumFeeWeighsOnSmallPurchases() {
        assertEquals(0.16, DictationCostEstimate.purchaseFeeRate(5.0), eps)
        assertEquals(0.08, DictationCostEstimate.purchaseFeeRate(10.0), eps)
        assertEquals(0.055, DictationCostEstimate.purchaseFeeRate(50.0), eps)
    }

    @Test
    fun brlRangeAppliesFeeIofAndRate() {
        assertEquals(0.766399, DictationCostEstimate.lowBrlPerHour(), eps)
        assertEquals(2.846370, DictationCostEstimate.highBrlPerHour(), eps)
        assertEquals(31.210197, DictationCostEstimate.minPurchaseBrl(), eps)
    }

    @Test
    fun labelRoundsTheRangeOutward() {
        val label = DictationCostEstimate.perHourLabel()

        assertTrue(label.startsWith("≈ R$ 0,70 a 2,90 "), label)
        assertTrue(DictationCostEstimate.minPurchaseLabel().contains("R$ 32"))
    }

    // Passada final medida: US$ 0,0049 por minuto de ditado, US$ 0,294 por hora.
    @Test
    fun theFinalPassOfTheLocalEngineCostsWhatWasMeasured() {
        assertEquals(0.294, DictationCostEstimate.finalPassUsdPerHour(), eps)
        assertTrue(DictationCostEstimate.finalPassPerHourLabel().contains("≈ R$ 1,60 a 1,90 por hora"))
    }
}
