package dev.rafaelbrauner.flowvoice.shared.stats

import dev.rafaelbrauner.flowvoice.shared.persist.MapRawKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// P59: o store é a fonte persistida das latências. Os testes prendem o formato do JSON único, o
// teto por métrica, a regra nearest-rank dos percentis e o backup do JSON ilegível (Y8).
class LatencyStatsTest {
    @Test
    fun summariesSurviveARestart() {
        val store = MapRawKeyValue()
        val first = LatencyStatsStore(store, clock = { 5_000L })
        first.record(LatencyStatsStore.METRIC_FINALIZE_CLOUD, 1_200L)
        first.record(LatencyStatsStore.METRIC_FINALIZE_CLOUD, 3_400L)
        first.record(LatencyStatsStore.METRIC_FINAL_PASS, 900L)

        val reopened = LatencyStatsStore(store, clock = { 5_000L })

        assertEquals(first.summaries(), reopened.summaries())
        assertEquals(3_400L, reopened.summaries().getValue(LatencyStatsStore.METRIC_FINALIZE_CLOUD).p95Ms)
        // Formato do P59: um JSON único com as amostras por métrica e o epoch ms do record.
        assertEquals(
            """{"samples":{"finalize_cloud":[{"ms":1200,"at":5000},{"ms":3400,"at":5000}],""" +
                """"final_pass":[{"ms":900,"at":5000}]}}""",
            store.values[KEY]
        )
    }

    @Test
    fun theCapKeepsOnlyTheNewestSamples() {
        val store = MapRawKeyValue()
        val stats = LatencyStatsStore(store, maxSamples = 100, clock = { 1L })

        for (ms in 1L..105L) stats.record(LatencyStatsStore.METRIC_FINALIZE_LOCAL, ms)

        val summary = stats.summaries().getValue(LatencyStatsStore.METRIC_FINALIZE_LOCAL)
        assertEquals(100, summary.count)
        // Restaram 6..105: as 5 mais antigas (1..5) saíram — p50 = 55 e p95 = 100 sobre as restantes.
        assertEquals(55L, summary.p50Ms)
        assertEquals(100L, summary.p95Ms)
        assertEquals(56L, summary.meanMs)
    }

    @Test
    fun percentilesFollowNearestRankOverSortedValues() {
        val store = MapRawKeyValue()
        val stats = LatencyStatsStore(store, clock = { 1L })

        for (ms in 1L..100L) stats.record(LatencyStatsStore.METRIC_FINAL_PASS, ms)
        for (ms in listOf(10L, 20L, 30L)) stats.record(LatencyStatsStore.METRIC_REVIEW_READY, ms)
        stats.record(LatencyStatsStore.METRIC_FINALIZE_CLOUD, 7L)

        val hundred = stats.summaries().getValue(LatencyStatsStore.METRIC_FINAL_PASS)
        assertEquals(50L, hundred.p50Ms)
        assertEquals(95L, hundred.p95Ms)
        assertEquals(100, hundred.count)

        val three = stats.summaries().getValue(LatencyStatsStore.METRIC_REVIEW_READY)
        assertEquals(20L, three.p50Ms)
        assertEquals(30L, three.p95Ms)

        val single = stats.summaries().getValue(LatencyStatsStore.METRIC_FINALIZE_CLOUD)
        assertEquals(7L, single.p50Ms)
        assertEquals(7L, single.p95Ms)
        assertEquals(1, single.count)
    }

    @Test
    fun unreadableStatsAreBackedUpAndRecordingContinues() {
        val raw = "não é json"
        val store = MapRawKeyValue(KEY to raw)
        val events = mutableListOf<String>()
        val stats = LatencyStatsStore(store, clock = { 1L }, log = { events += it })

        assertEquals(emptyMap(), stats.summaries())
        // Y8: o JSON ilegível nunca é sobrescrito sem cópia numa chave de backup.
        assertEquals(raw, store.values["$KEY.bak"])
        assertEquals(1, events.size)
        assertTrue(events.single().startsWith("latency_stats_unreadable "))
        assertTrue(events.single().endsWith("backup=$KEY.bak"))

        stats.record(LatencyStatsStore.METRIC_REVIEW_READY, 800L)

        assertEquals(1, stats.summaries().getValue(LatencyStatsStore.METRIC_REVIEW_READY).count)
    }

    @Test
    fun noOpNeverRecords() {
        val stats = LatencyStatsStore.NoOp

        stats.record(LatencyStatsStore.METRIC_FINALIZE_CLOUD, 100L)
        stats.reset()

        assertEquals(emptyMap(), stats.summaries())
    }

    @Test
    fun resetClearsAndPersistsEmpty() {
        val store = MapRawKeyValue()
        val stats = LatencyStatsStore(store, clock = { 1L })
        stats.record(LatencyStatsStore.METRIC_FINAL_PASS, 500L)

        stats.reset()

        assertEquals(emptyMap(), stats.summaries())
        assertEquals("""{"samples":{}}""", store.values[KEY])
        assertEquals(emptyMap(), LatencyStatsStore(store, clock = { 1L }).summaries())
    }

    @Test
    fun maxSamplesMustBePositive() {
        assertFailsWith<IllegalArgumentException> {
            LatencyStatsStore(MapRawKeyValue(), maxSamples = 0)
        }
    }

    private companion object {
        const val KEY = "latency_stats"
    }
}