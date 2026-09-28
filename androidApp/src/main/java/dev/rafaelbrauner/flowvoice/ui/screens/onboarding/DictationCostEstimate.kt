package dev.rafaelbrauner.flowvoice.ui.screens.onboarding

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindowAggregator
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// Estimativa de custo mostrada na tela da chave. Premissas e fontes ficam todas aqui; revise as
// datas antes de mudar o texto da tela.
object DictationCostEstimate {
    const val ESTIMATE_LABEL = "estimativa de set/2026"

    // deepgram/nova-3 (OpenRouterConfig.DEFAULT_MODEL), lido em 2026-09-28 de
    // https://openrouter.ai/api/v1/models?output_modalities=transcription: pricing.prompt =
    // 0.0000716666666667 por segundo de áudio. Bate com o `usage.cost` medido em
    // docs/medicao-modelos-nuvem.md (US$ 0,0043 por minuto). Premissa: cobrança por segundo, sem mínimo
    // por pedido; janelas mudas não são enviadas (P124), então a conta é por hora de fala.
    const val TRANSCRIPTION_USD_PER_AUDIO_SECOND = 0.0000716666666667

    // Cada janela leva 1 s do fim da anterior como contexto (P143). As janelas cortam na pausa
    // entre ~2 s e o alvo de 4 s (P140): quanto mais curtas, mais contexto repetido se paga.
    val CONTEXT_SECONDS = DictationWindowAggregator.SPEECH_CONTEXT_MS / 1000.0
    val LONG_WINDOW_SECONDS = DictationWindowAggregator.DEFAULT_TARGET_DURATION_MS / 1000.0
    const val SHORT_WINDOW_SECONDS = 2.0

    // Passada final com a revisão por IA ligada (nos dois motores, com os padrões): o áudio inteiro no
    // deepgram/nova-3 (US$ 0,0043/min) mais a formatação no openai/gpt-4.1-mini (US$ 0,0007/min), `usage.cost`
    // medido em docs/medicao-modelos-nuvem.md (2026-09-28), por minuto de ditado com as pausas.
    const val FINAL_PASS_USD_PER_MINUTE = 0.0050

    // Créditos pré-pagos: a OpenRouter cobra 5,5% por compra no cartão, mínimo de US$ 0,80
    // (https://openrouter.ai/docs/faq), e a menor compra é US$ 5 (https://openrouter.ai/terms),
    // ambos consultados em 2026-09-27. O piso supõe compras grandes (5,5%); o teto, a compra
    // mínima (US$ 0,80 sobre US$ 5 = 16%).
    const val OPENROUTER_FEE_RATE = 0.055
    const val OPENROUTER_MIN_FEE_USD = 0.80
    const val MIN_PURCHASE_USD = 5.0
    const val LARGE_PURCHASE_USD = 50.0

    // IOF de compra internacional no cartão em 2026: 3,5%.
    const val IOF_RATE = 0.035

    // PTAX de venda do Banco Central em 2026-09-25 (último dia útil antes de 2026-09-27):
    // https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/CotacaoDolarDia(dataCotacao=@dataCotacao)?@dataCotacao='09-25-2026'
    const val USD_TO_BRL = 5.1991

    fun audioOverheadFactor(windowSeconds: Double): Double = (windowSeconds + CONTEXT_SECONDS) / windowSeconds

    fun transcriptionUsdPerHour(windowSeconds: Double): Double =
        3600 * TRANSCRIPTION_USD_PER_AUDIO_SECOND * audioOverheadFactor(windowSeconds)

    fun purchaseFeeRate(purchaseUsd: Double): Double =
        max(purchaseUsd * OPENROUTER_FEE_RATE, OPENROUTER_MIN_FEE_USD) / purchaseUsd

    fun toBrl(usd: Double, purchaseUsd: Double): Double =
        usd * (1 + purchaseFeeRate(purchaseUsd)) * (1 + IOF_RATE) * USD_TO_BRL

    fun finalPassUsdPerHour(): Double = 60 * FINAL_PASS_USD_PER_MINUTE

    // Piso: só o ao vivo, janelas longas, compra grande. Teto: janelas curtas, a passada final (o áudio inteiro
    // de novo e a formatação) e a compra mínima.
    fun lowBrlPerHour(): Double = toBrl(transcriptionUsdPerHour(LONG_WINDOW_SECONDS), LARGE_PURCHASE_USD)

    fun highBrlPerHour(): Double =
        toBrl(transcriptionUsdPerHour(SHORT_WINDOW_SECONDS) + finalPassUsdPerHour(), MIN_PURCHASE_USD)

    fun minPurchaseBrl(): Double = toBrl(MIN_PURCHASE_USD, MIN_PURCHASE_USD)

    fun perHourLabel(): String {
        val low = floor(lowBrlPerHour() * 10) / 10
        val high = ceil(highBrlPerHour() * 10) / 10
        return "≈ R$ ${brl(low)} a ${brl(high)} por hora de ditado na nuvem (o teto com a revisão final por IA), " +
            ESTIMATE_LABEL
    }

    fun finalPassPerHourLabel(): String {
        val low = floor(toBrl(finalPassUsdPerHour(), LARGE_PURCHASE_USD) * 10) / 10
        val high = ceil(toBrl(finalPassUsdPerHour(), MIN_PURCHASE_USD) * 10) / 10
        return "Motor no aparelho com a revisão final por IA: ≈ R$ ${brl(low)} a ${brl(high)} por hora de ditado " +
            "(o áudio vai à nuvem no fim de cada ditado)"
    }

    fun minPurchaseLabel(): String = "US$ ${MIN_PURCHASE_USD.toInt()} (≈ R$ ${ceil(minPurchaseBrl()).toInt()} com taxa e IOF)"

    private fun brl(value: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value)
}
