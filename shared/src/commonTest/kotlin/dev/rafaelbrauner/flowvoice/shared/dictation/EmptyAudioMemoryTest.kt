package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmptyAudioMemoryTest {
    @Test
    fun withoutMemoryNothingIsSkipped() {
        assertFalse(EmptyAudioMemory().skips(400))
    }

    // O caso do S26 (2026-09-16 11:04): a janela que voltou vazia provou que áquele nível não tem
    // nada para transcrever. A seguinte, não mais alta que ela, não precisa ser paga de novo.
    @Test
    fun aWindowNoLouderThanOneThatCameBackEmptyIsSkipped() {
        val memory = EmptyAudioMemory().apply { remember(peak = 400, hasText = false) }

        assertTrue(memory.skips(400))
        assertTrue(memory.skips(320))
    }

    @Test
    fun aWindowLouderThanTheEmptyOneIsStillSent() {
        val memory = EmptyAudioMemory().apply { remember(peak = 400, hasText = false) }

        assertFalse(memory.skips(401))
    }

    // Se um nível igual ou mais baixo já rendeu texto nesta sessão, ele não é silêncio: quem fala
    // baixo continua sendo transcrito, mesmo depois de uma janela vazia mais alta.
    @Test
    fun aLevelThatAlreadyProducedTextIsNeverTakenForSilence() {
        val memory = EmptyAudioMemory().apply {
            remember(peak = 250, hasText = true)
            remember(peak = 400, hasText = false)
        }

        assertFalse(memory.skips(250))
        assertFalse(memory.skips(300))
        assertTrue(memory.skips(240))
    }

    @Test
    fun aWindowWithoutAMeasuredPeakIsNeverSkipped() {
        val memory = EmptyAudioMemory().apply { remember(peak = 400, hasText = false) }

        assertFalse(memory.skips(null))
    }

    // Janela sem pico medido não ensina nada: sem medição, não há o que comparar.
    @Test
    fun anEmptyWindowWithoutAMeasuredPeakTeachesNothing() {
        val memory = EmptyAudioMemory().apply { remember(peak = null, hasText = false) }

        assertFalse(memory.skips(400))
    }

    @Test
    fun theLoudestEmptyWindowIsTheOneThatCounts() {
        val memory = EmptyAudioMemory().apply {
            remember(peak = 400, hasText = false)
            remember(peak = 180, hasText = false)
        }

        assertTrue(memory.skips(390))
    }
}
