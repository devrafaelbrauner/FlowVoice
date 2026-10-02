package dev.rafaelbrauner.flowvoice.ui.screens.diagnostics

import dev.rafaelbrauner.flowvoice.shared.stats.LatencyStatsStore
import java.util.Locale

private const val NO_SOURCE = "—"

// Ordem fixa das métricas na linha, na sequência do ditado (finalize de cada motor, revisão,
// passada final). O mapa do store segue a ordem de gravação e não é contrato, então a linha não
// pode depender dele.
private val LATENCY_METRIC_LABELS = listOf(
    LatencyStatsStore.METRIC_FINALIZE_CLOUD to "nuvem",
    LatencyStatsStore.METRIC_FINALIZE_LOCAL to "aparelho",
    LatencyStatsStore.METRIC_REVIEW_READY to "revisão",
    LatencyStatsStore.METRIC_FINAL_PASS to "passada final"
)

// P59: a linha "latência p50 / p95" saía sempre "—" porque não havia fonte; o LatencyStatsStore
// agora guarda amostras persistidas por métrica e este resumo alimenta a linha da tela. Métrica
// sem amostra não aparece; sem nenhuma amostra a linha mantém o "—".
internal fun latencyRow(summaries: Map<String, LatencyStatsStore.LatencySummary>): String =
    LATENCY_METRIC_LABELS.mapNotNull { (metric, label) ->
        val summary = summaries[metric]
        if (summary == null || summary.count <= 0) return@mapNotNull null
        "$label ${formatLatencySeconds(summary.p50Ms)} / ${formatLatencySeconds(summary.p95Ms)}"
    }.joinToString(" · ")
        .ifEmpty { NO_SOURCE }

// Segundos com 1 casa decimal e vírgula pt-BR ("2,1 s"). O locale é fixado para a formatação não
// depender do idioma do aparelho.
internal fun formatLatencySeconds(ms: Long): String =
    "%.1f s".format(Locale("pt", "BR"), ms / 1000.0)
