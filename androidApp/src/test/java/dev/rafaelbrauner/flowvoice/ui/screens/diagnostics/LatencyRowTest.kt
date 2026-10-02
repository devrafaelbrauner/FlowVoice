package dev.rafaelbrauner.flowvoice.ui.screens.diagnostics

import dev.rafaelbrauner.flowvoice.shared.stats.LatencyStatsStore
import kotlin.test.Test
import kotlin.test.assertEquals

class LatencyRowTest {

    @Test
    fun formatsSecondsWithOneDecimalAndPtBrComma() {
        assertEquals("0,6 s", formatLatencySeconds(649))
        assertEquals("0,7 s", formatLatencySeconds(651))
        assertEquals("2,1 s", formatLatencySeconds(2100))
        assertEquals("12,3 s", formatLatencySeconds(12345))
    }

    @Test
    fun rowJoinsMetricsWithP50AndP95InFixedOrder() {
        val line = latencyRow(
            mapOf(
                LatencyStatsStore.METRIC_REVIEW_READY to summary(p50Ms = 5100, p95Ms = 6100),
                LatencyStatsStore.METRIC_FINALIZE_CLOUD to summary(p50Ms = 2100, p95Ms = 3400)
            )
        )

        // A ordem é fixa (finalize dos motores, revisão, passada final), não a ordem do mapa.
        assertEquals("nuvem 2,1 s / 3,4 s · revisão 5,1 s / 6,1 s", line)
    }

    @Test
    fun rowWithoutSamplesStaysNoSource() {
        assertEquals("—", latencyRow(emptyMap()))
    }

    @Test
    fun rowSkipsMetricsWithoutSamples() {
        val line = latencyRow(
            mapOf(
                LatencyStatsStore.METRIC_FINALIZE_LOCAL to summary(p50Ms = 610, p95Ms = 1200),
                LatencyStatsStore.METRIC_FINAL_PASS to summary(count = 0, p50Ms = 900, p95Ms = 900)
            )
        )

        assertEquals("aparelho 0,6 s / 1,2 s", line)
    }

    private fun summary(count: Int = 3, p50Ms: Long, p95Ms: Long): LatencyStatsStore.LatencySummary =
        LatencyStatsStore.LatencySummary(count = count, p50Ms = p50Ms, p95Ms = p95Ms, meanMs = p50Ms)
}
