package dev.rafaelbrauner.flowvoice.shared.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BenchmarkBudgetTest {
    @Test
    fun defaultCapIsOneDollar() {
        val budget = BenchmarkBudget()
        assertEquals(1.0, budget.maxUsd)
        assertEquals(40, budget.maxRequests)
        assertEquals(8, TranscriptionShortlist.roundOne.size)
        assertEquals(TranscriptionShortlist.BASELINE, TranscriptionShortlist.roundOne.first())
    }

    @Test
    fun rejectsNonPositiveLimits() {
        assertFailsWith<IllegalArgumentException> { BenchmarkBudget(maxUsd = 0.0) }
        assertFailsWith<IllegalArgumentException> { BenchmarkBudget(maxRequests = 0) }
    }
}
