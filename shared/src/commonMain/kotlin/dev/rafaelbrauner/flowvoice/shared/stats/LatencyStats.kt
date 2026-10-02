package dev.rafaelbrauner.flowvoice.shared.stats

import dev.rafaelbrauner.flowvoice.shared.persist.RawKeyValue
import dev.rafaelbrauner.flowvoice.shared.persist.backUp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

// P59: as latências medidas (dictation_ready latencyMs, local_commit etc.) não tinham fonte
// persistida e as telas mostravam "—". O store guarda amostras por métrica num único JSON, no
// mesmo padrão do JsonDictionaryPersist, e resume p50/p95/média para alimentar as telas.
class LatencyStatsStore(
    private val persist: RawKeyValue,
    private val key: String = "latency_stats",
    private val maxSamples: Int = 100,
    private val log: (String) -> Unit = {},
    // Epoch ms do momento do record. commonMain não tem relógio de parede: injetado como o
    // SyncEngine; o default 0 mantém o contrato construtível e a fiação passa System.currentTimeMillis().
    private val clock: () -> Long = { 0L }
) {
    private var recording = true
    private var samples: Map<String, List<Sample>> = emptyMap()

    init {
        require(maxSamples > 0) { "maxSamples must be positive" }
        val raw = persist.get(key)
        if (raw != null) {
            samples = runCatching { decode(raw) }.getOrElse { error ->
                // Y8: JSON ilegível nunca é sobrescrito sem cópia — mesmo contrato do dicionário.
                val backup = persist.backUp(key, raw)
                log("latency_stats_unreadable ${error::class.simpleName} backup=$backup")
                emptyMap()
            }
        }
    }

    fun record(metric: String, ms: Long) {
        if (!recording) return
        // O teto é por métrica: acima de maxSamples, sai a amostra mais antiga.
        val updated = (samples[metric].orEmpty() + Sample(ms = ms, at = clock())).takeLast(maxSamples)
        samples = samples + (metric to updated)
        persist.put(key, encode(samples))
    }

    // Métrica sem amostras nem entra no mapa: a tela continua mostrando "—" em vez de zeros falsos.
    fun summaries(): Map<String, LatencySummary> =
        if (!recording) {
            emptyMap()
        } else {
            samples.entries
                .filter { it.value.isNotEmpty() }
                .associate { (metric, values) -> metric to summarize(values) }
        }

    fun reset() {
        if (!recording) return
        samples = emptyMap()
        // Grava o JSON vazio em vez de apagar a chave: reler não encontra lixo antigo.
        persist.put(key, encode(emptyMap()))
    }

    private fun summarize(samples: List<Sample>): LatencySummary {
        val sorted = samples.map { it.ms }.sorted()
        return LatencySummary(
            count = sorted.size,
            p50Ms = nearestRank(sorted, numerator = 1, denominator = 2),
            p95Ms = nearestRank(sorted, numerator = 19, denominator = 20),
            // Média arredondada para o ms mais próximo; empates para cima (55,5 -> 56).
            meanMs = (sorted.sum().toDouble() / sorted.size).roundToLong()
        )
    }

    // Regra nearest-rank: o percentil p sobre n valores ordenados é o valor na posição 1-based
    // ceil(p*n) — p50 de 4 amostras é a 2ª menor; p95 de 100 é a 95ª menor. O teto sai em inteiros
    // (ceil(a/b) = (a+b-1)/b) para não herdar o arredondamento binário de frações como 0.95.
    private fun nearestRank(sorted: List<Long>, numerator: Int, denominator: Int): Long =
        sorted[(numerator * sorted.size + denominator - 1) / denominator - 1]

    private fun decode(raw: String): Map<String, List<Sample>> =
        Json.decodeFromString(SamplesFile.serializer(), raw).samples

    private fun encode(samples: Map<String, List<Sample>>): String =
        Json.encodeToString(SamplesFile.serializer(), SamplesFile(samples))

    data class LatencySummary(val count: Int, val p50Ms: Long, val p95Ms: Long, val meanMs: Long)

    // Formato persistido: {"samples":{"finalize_cloud":[{"ms":1234,"at":1760000000000},...],...}}
    @Serializable
    private data class Sample(val ms: Long, val at: Long)

    @Serializable
    private data class SamplesFile(val samples: Map<String, List<Sample>>)

    companion object {
        // finalize_cloud/finalize_local = toque de parar até o texto final no campo (ditado direto,
        // um por motor); review_ready = toque até o texto pronto na barra de revisão; final_pass =
        // duração da passada final.
        const val METRIC_FINALIZE_CLOUD = "finalize_cloud"
        const val METRIC_FINALIZE_LOCAL = "finalize_local"
        const val METRIC_REVIEW_READY = "review_ready"
        const val METRIC_FINAL_PASS = "final_pass"

        // Para testes do pipeline: record não grava, summaries() vem vazio e reset é inofensivo.
        val NoOp: LatencyStatsStore = LatencyStatsStore(Blank).also { it.recording = false }

        private object Blank : RawKeyValue {
            override fun get(key: String): String? = null
            override fun put(key: String, value: String) {}
        }
    }
}