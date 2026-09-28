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
        val memory = EmptyAudioMemory().apply { rememberEmpty(peak = 400, noiseFloor = QUIET_ROOM) }

        assertTrue(memory.skips(400))
        assertTrue(memory.skips(320))
    }

    @Test
    fun aWindowLouderThanTheEmptyOneIsStillSent() {
        val memory = EmptyAudioMemory().apply { rememberEmpty(peak = 400, noiseFloor = QUIET_ROOM) }

        assertFalse(memory.skips(401))
    }

    // Se um nível igual ou mais baixo já rendeu texto nesta sessão, ele não é silêncio: quem fala
    // baixo continua sendo transcrito, mesmo depois de uma janela vazia mais alta.
    @Test
    fun aLevelThatAlreadyProducedTextIsNeverTakenForSilence() {
        val memory = EmptyAudioMemory().apply {
            rememberSpoken(peak = 250, noiseFloor = QUIET_ROOM)
            rememberEmpty(peak = 400, noiseFloor = QUIET_ROOM)
        }

        assertFalse(memory.skips(250))
        assertFalse(memory.skips(300))
        assertTrue(memory.skips(240))
    }

    @Test
    fun aWindowWithoutAMeasuredPeakIsNeverSkipped() {
        val memory = EmptyAudioMemory().apply { rememberEmpty(peak = 400, noiseFloor = QUIET_ROOM) }

        assertFalse(memory.skips(null))
    }

    // Janela sem pico medido não ensina nada: sem medição, não há o que comparar.
    @Test
    fun anEmptyWindowWithoutAMeasuredPeakTeachesNothing() {
        val memory = EmptyAudioMemory().apply { rememberEmpty(peak = null, noiseFloor = QUIET_ROOM) }

        assertFalse(memory.skips(400))
    }

    @Test
    fun theLoudestEmptyWindowIsTheOneThatCounts() {
        val memory = EmptyAudioMemory().apply {
            rememberEmpty(peak = 400, noiseFloor = QUIET_ROOM)
            rememberEmpty(peak = 180, noiseFloor = QUIET_ROOM)
        }

        assertTrue(memory.skips(390))
    }

    // R1: uma tosse (pico 6000) que voltou vazia não é silêncio. Aprender com ela pulava toda a fala
    // mais baixa do resto do ditado — fala a um braço do aparelho fica em 2500–3000.
    @Test
    fun aLoudEmptyWindowTeachesNothingAndIsReportedAsSuspicious() {
        val memory = EmptyAudioMemory()

        assertFalse(memory.rememberEmpty(peak = 6_000, noiseFloor = QUIET_ROOM), "voz alta sem texto é suspeita")
        assertFalse(memory.skips(3_000))
        assertFalse(memory.skips(2_500))
        assertFalse(memory.skips(300))
    }

    // O teto acompanha o ruído da sessão, como o limiar de fala do endpointer: numa sala barulhenta
    // (piso 200, limiar 600) um vazio em 1000 ainda é ruído, e é aprendido.
    @Test
    fun theSilenceCeilingFollowsTheSessionNoiseFloor() {
        val memory = EmptyAudioMemory()

        assertTrue(memory.rememberEmpty(peak = 1_000, noiseFloor = 200))
        assertTrue(memory.skips(900))
        assertFalse(EmptyAudioMemory().rememberEmpty(peak = 1_000, noiseFloor = QUIET_ROOM))
    }

    // A janela do fim (flush) não traz piso: vale o último medido na sessão.
    @Test
    fun aWindowWithoutNoiseFloorUsesTheLastOneMeasured() {
        val memory = EmptyAudioMemory().apply { rememberSpoken(peak = 3_000, noiseFloor = 200) }

        assertTrue(memory.rememberEmpty(peak = 1_000, noiseFloor = null))
    }

    private companion object {
        const val QUIET_ROOM = 16
    }
}
