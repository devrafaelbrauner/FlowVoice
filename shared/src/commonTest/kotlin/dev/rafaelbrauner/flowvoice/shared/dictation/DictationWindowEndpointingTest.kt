package dev.rafaelbrauner.flowvoice.shared.dictation

import dev.rafaelbrauner.flowvoice.shared.transcription.voicedPcm
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Corte na pausa natural da fala (P140), com PCM sintético: fala = amostras de ±1000, silêncio =
// zeros e ruído de sala = amostras alternando ±amplitude (nível médio igual à amplitude).
class DictationWindowEndpointingTest {
    private class Emitted(val atMs: Long, val window: DictationWindow)

    private fun bytes(ms: Long): Int = (ms * BYTES_PER_MS).toInt()

    private fun silence(ms: Long) = ByteArray(bytes(ms))

    private fun speech(ms: Long) = voicedPcm(bytes(ms))

    private fun noise(ms: Long, amplitude: Int = ROOM_NOISE) = ByteArray(bytes(ms)).also { pcm ->
        for (sample in 0 until pcm.size / 2) writeSample(pcm, sample, if (sample % 2 == 0) amplitude else -amplitude)
    }

    // Fala sem pausa, com a variação de nível das sílabas: 150 ms fortes e 100 ms fracos.
    private fun syllabicSpeech(ms: Long): ByteArray = ByteArray(bytes(ms)).also { pcm ->
        for (sample in 0 until pcm.size / 2) {
            val amplitude = if ((sample / 16) % 250 < 150) 2_000 else 400
            writeSample(pcm, sample, if (sample % 2 == 0) amplitude else -amplitude)
        }
    }

    // Ruído de sala (ou silêncio, com amplitude 0) com estalos curtos (teclas, toques na mesa) de
    // `clickMs` a cada `everyMs`.
    private fun noiseWithClicks(
        ms: Long,
        everyMs: Long,
        noiseAmplitude: Int = ROOM_NOISE,
        clickMs: Long = CLICK_MS
    ): ByteArray = noise(ms, noiseAmplitude).also { pcm ->
        var startMs = everyMs
        while (startMs + clickMs <= ms) {
            val first = bytes(startMs) / 2
            for (sample in first until first + bytes(clickMs) / 2) {
                writeSample(pcm, sample, if (sample % 2 == 0) CLICK_AMPLITUDE else -CLICK_AMPLITUDE)
            }
            startMs += everyMs
        }
    }

    private fun writeSample(pcm: ByteArray, sample: Int, value: Int) {
        pcm[sample * 2] = (value and 0xFF).toByte()
        pcm[sample * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun aggregator() = DictationWindowAggregator(
        pauseSearchBeforeMs = DictationWindowAggregator.SPEECH_PAUSE_SEARCH_BEFORE_MS,
        pauseSearchAfterMs = DictationWindowAggregator.SPEECH_PAUSE_SEARCH_AFTER_MS,
        endpointing = SpeechEndpointing()
    )

    private fun feed(
        aggregator: DictationWindowAggregator,
        pcm: ByteArray,
        frameBytes: Int = FRAME_100_MS_BYTES
    ): List<Emitted> {
        val emitted = mutableListOf<Emitted>()
        var fed = 0
        while (fed < pcm.size) {
            val end = min(pcm.size, fed + frameBytes)
            val windows = aggregator.onFrame(AudioFrame(pcm.copyOfRange(fed, end)))
            fed = end
            windows.forEach { emitted += Emitted(AudioFormat.DEFAULT.durationMs(fed), it) }
        }
        return emitted
    }

    @Test
    fun shortPhraseFollowedByAPauseIsEmittedRightAfterThePause() {
        val emitted = feed(aggregator(), silence(300L) + speech(1_000L) + silence(800L))

        val first = emitted.single()
        // A pausa começa em 1300 ms, mas a janela só sai ao acumular os 2 s mínimos (P143).
        assertEquals(2_000L, first.atMs, "a janela sai no frame em que o mínimo acumulado é atingido")
        assertEquals(WindowCut.Pause, first.window.cut)
        assertTrue(first.window.finishedAtMs in 1_300L..2_000L, "corte em ${first.window.finishedAtMs} ms, fora da pausa")
    }

    // 300 ms era o mínimo antes da P143 e cortava aqui; agora uma hesitação desse tamanho no meio da
    // frase não parte mais a frase.
    @Test
    fun pauseShorterThanTheMinimumDoesNotCut() {
        val emitted = feed(aggregator(), speech(1_500L) + silence(300L) + speech(1_500L))

        assertTrue(emitted.isEmpty(), "cortou em ${emitted.map { it.window.finishedAtMs }}")
    }

    @Test
    fun continuousSpeechIsCutAtTheCeiling() {
        val emitted = feed(aggregator(), speech(6_000L))

        val first = emitted.first()
        assertEquals(4_300L, first.atMs)
        assertEquals(WindowCut.Ceiling, first.window.cut)
        assertTrue(first.window.durationMs in 3_100L..4_300L, "janela de ${first.window.durationMs} ms")
    }

    @Test
    fun syllabicSpeechWithoutPausesFromTheFirstFrameIsOnlyCutAtTheCeiling() {
        val emitted = feed(aggregator(), syllabicSpeech(6_000L))

        val first = emitted.first()
        assertEquals(4_300L, first.atMs)
        assertEquals(WindowCut.Ceiling, first.window.cut)
    }

    @Test
    fun steadyRoomNoiseStillLetsARelativePauseCut() {
        // Um limiar fixo no piso absoluto nunca veria pausa nesse ruído.
        assertTrue(ROOM_NOISE > SpeechEndpointing.MIN_PAUSE_LEVEL)

        val emitted = feed(aggregator(), noise(500L) + speech(1_500L) + noise(500L) + speech(1_000L))

        val first = emitted.single()
        assertEquals(2_500L, first.atMs)
        assertEquals(WindowCut.Pause, first.window.cut)
        assertTrue(first.window.finishedAtMs in 2_000L..2_500L, "corte em ${first.window.finishedAtMs} ms, fora da pausa")
        assertNotNull(first.window.noiseFloor)
        assertTrue(first.window.noiseFloor!! in 200..400, "piso de ruído ${first.window.noiseFloor}")
    }

    @Test
    fun doesNotEmitBeforeTheMinimumIsBuffered() {
        val emitted = feed(aggregator(), speech(500L) + silence(1_500L))

        val first = emitted.single()
        assertEquals(SpeechEndpointing.MIN_BUFFERED_MS, first.atMs)
        assertEquals(WindowCut.Pause, first.window.cut)
        assertTrue(first.window.finishedAtMs in 500L..2_000L, "corte em ${first.window.finishedAtMs} ms, fora da pausa")
    }

    @Test
    fun pauseWithoutSpeechBeforeItKeepsAccumulating() {
        assertTrue(feed(aggregator(), silence(4_200L)).isEmpty())
        assertTrue(feed(aggregator(), noise(4_200L)).isEmpty())
        assertTrue(feed(aggregator(), silence(1_000L) + speech(300L) + silence(2_900L)).isEmpty(), "300 ms de som não são fala bastante")
    }

    @Test
    fun speechAfterALongSilenceIsNotCutInTheMiddleAtTheCeiling() {
        val emitted = feed(aggregator(), silence(2_900L) + speech(3_000L))

        val first = emitted.first()
        assertEquals(4_300L, first.atMs)
        assertEquals(WindowCut.Leading, first.window.cut)
        assertTrue(first.window.finishedAtMs in 2_500L..2_900L, "corte em ${first.window.finishedAtMs} ms, dentro da fala")
    }

    @Test
    fun silenceAndRoomNoiseDoNotMultiplyWindows() {
        listOf(
            "silêncio" to silence(13_000L),
            "ruído" to noise(13_000L),
            "ruído com estalos" to noiseWithClicks(13_000L, everyMs = 250L),
            // Pausas de 300 ms entre estalos de 60 ms: somados passam de 400 ms, mas nenhum é fala.
            "silêncio com estalos" to noiseWithClicks(13_000L, everyMs = 360L, noiseAmplitude = 0, clickMs = 60L)
        ).forEach { (name, pcm) ->
            val windows = feed(aggregator(), pcm).map { it.window }

            assertTrue(windows.none { it.cut == WindowCut.Pause }, "$name: cortes ${windows.map { it.cut }}")
            assertTrue(windows.size <= 3, "$name: ${windows.size} janelas em 13 s")
            windows.forEach { assertTrue(it.durationMs >= 3_100L, "$name: janela de ${it.durationMs} ms") }
        }
    }

    @Test
    fun everyByteIsKeptAndTheTimelineContinuesWithFramesOf100And256Ms() {
        val pcm = silence(700L) + speech(1_800L) + silence(450L) + speech(5_200L) + noise(1_000L) +
            speech(900L) + silence(2_500L) + speech(1_200L)

        listOf(FRAME_100_MS_BYTES, FRAME_256_MS_BYTES, 7_056).forEach { frameBytes ->
            val aggregator = aggregator()
            val windows = feed(aggregator, pcm, frameBytes).map { it.window } + listOfNotNull(aggregator.flush())

            assertTrue(windows.size >= 4, "frames de $frameBytes bytes: ${windows.size} janelas")
            assertContentEquals(pcm, windows.map { it.pcm }.reduce { acc, next -> acc + next })
            assertEquals(windows.indices.toList(), windows.map { it.index })
            assertEquals(0L, windows.first().startedAtMs)
            windows.zipWithNext().forEach { (previous, next) -> assertEquals(previous.finishedAtMs, next.startedAtMs) }
            assertEquals(AudioFormat.DEFAULT.durationMs(pcm.size), windows.last().finishedAtMs)
            val frameMs = AudioFormat.DEFAULT.durationMs(frameBytes) + 1
            windows.forEach { assertTrue(it.durationMs <= 4_300L + frameMs, "janela de ${it.durationMs} ms") }
        }
    }

    @Test
    fun framesOf256MsStillCutInsideThePause() {
        val emitted = feed(aggregator(), silence(300L) + speech(1_000L) + silence(800L), FRAME_256_MS_BYTES)

        val first = emitted.single()
        assertEquals(2_048L, first.atMs, "primeiro frame de 256 ms que termina depois do mínimo acumulado")
        assertEquals(WindowCut.Pause, first.window.cut)
        assertTrue(first.window.finishedAtMs in 1_300L..2_048L, "corte em ${first.window.finishedAtMs} ms, fora da pausa")
    }

    @Test
    fun flushEmitsTheRemainderAtTheEnd() {
        val aggregator = aggregator()

        val cut = feed(aggregator, speech(1_000L) + silence(500L) + speech(700L)).single().window
        val tail = aggregator.flush()

        assertNotNull(tail)
        assertEquals(WindowCut.Flush, tail.cut)
        assertEquals(1, tail.index)
        assertEquals(cut.finishedAtMs, tail.startedAtMs)
        assertEquals(2_200L, tail.finishedAtMs)
        assertNull(aggregator.flush())
    }

    @Test
    fun clearForgetsTheNoiseFloorOfThePreviousSession() {
        val aggregator = aggregator()
        // Piso de 2000 com contraste (estalos): sem limpar, a fala de nível 1000 seria pausa.
        feed(aggregator, noiseWithClicks(10_000L, everyMs = 250L, noiseAmplitude = 2_000))
        aggregator.clear()

        val first = feed(aggregator, silence(300L) + speech(1_000L) + silence(800L)).single()

        assertEquals(2_000L, first.atMs)
        assertEquals(0, first.window.index)
        assertEquals(WindowCut.Pause, first.window.cut)
    }

    @Test
    fun endpointingRequires16BitPcm() {
        assertFailsWith<IllegalArgumentException> {
            DictationWindowAggregator(format = AudioFormat(sampleBits = 8), endpointing = SpeechEndpointing())
        }
    }

    private companion object {
        const val BYTES_PER_MS = 32L
        const val FRAME_100_MS_BYTES = 3_200
        const val FRAME_256_MS_BYTES = 8_192
        const val ROOM_NOISE = 300
        const val CLICK_MS = 40L
        const val CLICK_AMPLITUDE = 8_000
    }
}
