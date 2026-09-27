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

    // openai/gpt-transcribe (OpenRouterConfig.DEFAULT_MODEL), lido em 2026-09-27 de
    // https://openrouter.ai/api/v1/models?output_modalities=transcription: pricing.prompt =
    // 0.000075 por segundo de áudio. Bate com o benchmark F05 (docs/PLAN.md): US$ 0,001125 por
    // um clipe de 15 s. Premissa: cobrança por segundo, sem mínimo por pedido; janelas mudas não
    // são enviadas (P124), então a conta é por hora de fala.
    const val TRANSCRIPTION_USD_PER_AUDIO_SECOND = 0.000075

    // Cada janela leva 1 s do fim da anterior como contexto (P143). As janelas cortam na pausa
    // entre ~2 s e o alvo de 4 s (P140): quanto mais curtas, mais contexto repetido se paga.
    val CONTEXT_SECONDS = DictationWindowAggregator.SPEECH_CONTEXT_MS / 1000.0
    val LONG_WINDOW_SECONDS = DictationWindowAggregator.DEFAULT_TARGET_DURATION_MS / 1000.0
    const val SHORT_WINDOW_SECONDS = 2.0

    // Revisão por IA (desligada por padrão; AppPreferences.DEFAULT_PROOFREADING_MODEL =
    // openai/gpt-4o-mini), lido em 2026-09-27 de https://openrouter.ai/api/v1/models:
    // US$ 0,15 por milhão de tokens de entrada e US$ 0,60 por milhão de saída. Só entra no teto.
    const val PROOFREADING_INPUT_USD_PER_TOKEN = 0.00000015
    const val PROOFREADING_OUTPUT_USD_PER_TOKEN = 0.0000006
    // Fala contínua de ~150 palavras por minuto, ~1,6 token por palavra em português.
    const val WORDS_PER_MINUTE = 150.0
    const val TOKENS_PER_WORD = 1.6
    // Um ditado a cada 30 s, cada um com o prompt de sistema da revisão (~110 tokens).
    const val PROOFREADING_CALLS_PER_HOUR = 120.0
    const val PROOFREADING_PROMPT_TOKENS = 110.0

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

    fun proofreadingUsdPerHour(): Double {
        val textTokens = WORDS_PER_MINUTE * 60 * TOKENS_PER_WORD
        val input = textTokens + PROOFREADING_CALLS_PER_HOUR * PROOFREADING_PROMPT_TOKENS
        return input * PROOFREADING_INPUT_USD_PER_TOKEN + textTokens * PROOFREADING_OUTPUT_USD_PER_TOKEN
    }

    fun purchaseFeeRate(purchaseUsd: Double): Double =
        max(purchaseUsd * OPENROUTER_FEE_RATE, OPENROUTER_MIN_FEE_USD) / purchaseUsd

    fun toBrl(usd: Double, purchaseUsd: Double): Double =
        usd * (1 + purchaseFeeRate(purchaseUsd)) * (1 + IOF_RATE) * USD_TO_BRL

    fun lowBrlPerHour(): Double = toBrl(transcriptionUsdPerHour(LONG_WINDOW_SECONDS), LARGE_PURCHASE_USD)

    fun highBrlPerHour(): Double =
        toBrl(transcriptionUsdPerHour(SHORT_WINDOW_SECONDS) + proofreadingUsdPerHour(), MIN_PURCHASE_USD)

    fun minPurchaseBrl(): Double = toBrl(MIN_PURCHASE_USD, MIN_PURCHASE_USD)

    fun perHourLabel(): String {
        val low = floor(lowBrlPerHour() * 10) / 10
        val high = ceil(highBrlPerHour() * 10) / 10
        return "≈ R$ ${brl(low)} a ${brl(high)} por hora de ditado, $ESTIMATE_LABEL"
    }

    fun minPurchaseLabel(): String = "US$ ${MIN_PURCHASE_USD.toInt()} (≈ R$ ${ceil(minPurchaseBrl()).toInt()} com taxa e IOF)"

    private fun brl(value: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value)
}
