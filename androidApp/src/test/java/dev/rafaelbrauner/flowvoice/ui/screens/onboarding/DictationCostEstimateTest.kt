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
    fun hourOfSpeechAtGptTranscribePrice() {
        // 3600 s × US$ 0,000075/s = US$ 0,27 de áudio, mais o contexto repetido.
        assertEquals(0.3375, DictationCostEstimate.transcriptionUsdPerHour(4.0), eps)
        assertEquals(0.405, DictationCostEstimate.transcriptionUsdPerHour(2.0), eps)
    }

    @Test
    fun proofreadingAddsLittleNextToTranscription() {
        // 14.400 tokens de texto + 120 × 225 de prompt na entrada; 14.400 na saída (claude-haiku-4.5).
        assertEquals(0.1134, DictationCostEstimate.proofreadingUsdPerHour(), eps)
    }

    @Test
    fun minimumFeeWeighsOnSmallPurchases() {
        assertEquals(0.16, DictationCostEstimate.purchaseFeeRate(5.0), eps)
        assertEquals(0.08, DictationCostEstimate.purchaseFeeRate(10.0), eps)
        assertEquals(0.055, DictationCostEstimate.purchaseFeeRate(50.0), eps)
    }

    @Test
    fun brlRangeAppliesFeeIofAndRate() {
        assertEquals(1.915997, DictationCostEstimate.lowBrlPerHour(), eps)
        assertEquals(3.235873, DictationCostEstimate.highBrlPerHour(), eps)
        assertEquals(31.210197, DictationCostEstimate.minPurchaseBrl(), eps)
    }

    @Test
    fun labelRoundsTheRangeOutward() {
        val label = DictationCostEstimate.perHourLabel()

        assertTrue(label.startsWith("≈ R$ 1,90 a 3,30 "), label)
        assertTrue(DictationCostEstimate.minPurchaseLabel().contains("R$ 32"))
    }

    // Passada final medida: US$ 0,0073 por minuto de ditado, US$ 0,438 por hora.
    @Test
    fun theFinalPassOfTheLocalEngineCostsWhatWasMeasured() {
        assertEquals(0.438, DictationCostEstimate.finalPassUsdPerHour(), eps)
        assertTrue(DictationCostEstimate.finalPassPerHourLabel().contains("≈ R$ 2,40 a 2,80 por hora"))
    }
}
