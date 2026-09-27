package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureTailTest {
    // A primeira leitura vazia pode ser o destravar do stop() ainda pendente; o que vem depois dela é
    // a cauda, e a vazia seguinte encerra.
    @Test
    fun drainsWhatTheBufferStillHoldsPastTheUnblockingEmptyRead() {
        val reads = ArrayDeque(listOf(0, 3_200, 1_600, 0, 3_200))
        val delivered = mutableListOf<Int>()

        val drained = CaptureTail.drain(maxBytes = 12_800, read = { reads.removeFirst() }, deliver = { delivered += it }, waiting = { true })

        assertEquals(listOf(3_200, 1_600), delivered)
        assertEquals(4_800, drained)
    }

    // Um buffer que nunca esvazia (plataforma que continua entregando) não prende a thread de captura.
    @Test
    fun neverReadsPastTheBufferSize() {
        val delivered = mutableListOf<Int>()

        CaptureTail.drain(maxBytes = 6_400, read = { 3_200 }, deliver = { delivered += it }, waiting = { true })

        assertEquals(listOf(3_200, 3_200), delivered)
    }

    // Quem para a captura deixou de esperar (o join estourou, outra sessão pode ter começado): nada
    // mais é entregue.
    @Test
    fun stopsAsSoonAsTheStopNoLongerWaits() {
        var waiting = true
        val delivered = mutableListOf<Int>()

        CaptureTail.drain(
            maxBytes = 12_800,
            read = { 3_200 },
            deliver = { delivered += it; waiting = false },
            waiting = { waiting }
        )

        assertEquals(listOf(3_200), delivered)
    }

    @Test
    fun anErrorCodeEndsTheDrain() {
        val reads = ArrayDeque(listOf(3_200, -3, 3_200))
        val delivered = mutableListOf<Int>()

        CaptureTail.drain(maxBytes = 12_800, read = { reads.removeFirst() }, deliver = { delivered += it }, waiting = { true })

        assertEquals(listOf(3_200), delivered)
    }
}
