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
        // 14.400 tokens de texto + 120 × 110 de prompt na entrada; 14.400 na saída.
        assertEquals(0.01278, DictationCostEstimate.proofreadingUsdPerHour(), eps)
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
        assertEquals(2.607799, DictationCostEstimate.highBrlPerHour(), eps)
        assertEquals(31.210197, DictationCostEstimate.minPurchaseBrl(), eps)
    }

    @Test
    fun labelRoundsTheRangeOutward() {
        val label = DictationCostEstimate.perHourLabel()

        assertTrue(label.startsWith("≈ R$ 1,90 a 2,70 "), label)
        assertTrue(DictationCostEstimate.minPurchaseLabel().contains("R$ 32"))
    }
}
