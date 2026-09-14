package dev.rafaelbrauner.flowvoice.ui.screens.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

class DiagnosticsLogTest {
    private fun log(maxLines: Int = 80): DiagnosticsLog {
        var now = 0L
        return DiagnosticsLog(maxLines = maxLines, clock = { ++now }, timeFormatter = { "t$it" })
    }

    @Test
    fun newestLineComesFirstWithTimestamp() {
        val log = log()

        log.add("primeiro")
        log.add("segundo")

        assertEquals(listOf("[t2] segundo", "[t1] primeiro"), log.lines.value)
    }

    @Test
    fun keepsOnlyTheLastEightyLines() {
        val log = log()

        repeat(85) { log.add("m${it + 1}") }

        val lines = log.lines.value
        assertEquals(80, lines.size)
        assertEquals("[t85] m85", lines.first())
        assertEquals("[t6] m6", lines.last())
    }

    @Test
    fun respectsCustomLimit() {
        val log = log(maxLines = 2)

        log.add("a")
        log.add("b")
        log.add("c")

        assertEquals(listOf("[t3] c", "[t2] b"), log.lines.value)
    }
}
