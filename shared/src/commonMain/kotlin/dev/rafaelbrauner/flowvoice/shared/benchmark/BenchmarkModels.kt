package dev.rafaelbrauner.flowvoice.shared.benchmark

data class ClipScore(
    val clipId: String,
    val hypothesis: String,
    val wer: Double,
    val latencyMs: Long,
    val costUsd: Double?,
    val errorKind: String? = null
)

data class ModelScore(
    val model: String,
    val clips: List<ClipScore>,
    val meanWer: Double?,
    val meanLatencyMs: Long?,
    val totalCostUsd: Double,
    val successes: Int,
    val failures: Int
)

data class BenchmarkReport(
    val models: List<ModelScore>,
    val defaultModel: String?,
    val fallbackModel: String?,
    val spentUsd: Double,
    val requests: Int,
    val stoppedReason: String?
) {
    fun rankingText(): String {
        if (models.isEmpty()) return "sem resultados"
        val lines = models.mapIndexed { index, score ->
            val wer = score.meanWer?.toString() ?: "n/d"
            val latency = score.meanLatencyMs?.toString() ?: "n/d"
            "${index + 1}. ${score.model}  WER=$wer  lat=${latency}ms  custo=${score.totalCostUsd}  ok=${score.successes}/${score.successes + score.failures}"
        }
        return buildString {
            appendLine("padrão: ${defaultModel ?: "n/d"}")
            appendLine("fallback: ${fallbackModel ?: "n/d"}")
            appendLine("gasto: $spentUsd  req: $requests")
            if (stoppedReason != null) appendLine("parada: $stoppedReason")
            lines.forEach { appendLine(it) }
        }.trim()
    }
}
