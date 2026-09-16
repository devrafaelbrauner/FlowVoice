package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Contexto sobreposto em áudio (P143): cada janela leva o fim do áudio da janela anterior, para o
// modelo não transcrever um trecho curto sem nenhuma pista do que veio antes. O contexto viaja
// separado do `pcm`: a linha do tempo, a contagem de bytes e a detecção de silêncio (P124) só olham
// o áudio próprio da janela.
class DictationWindowContextTest {
    private fun frameOf(durationMs: Long, fill: Byte): AudioFrame =
        AudioFrame(ByteArray((durationMs * BYTES_PER_MS).toInt()) { fill }, AudioFormat.DEFAULT)

    private fun aggregator(contextDurationMs: Long = CONTEXT_MS) =
        DictationWindowAggregator(targetDurationMs = 100L, contextDurationMs = contextDurationMs)

    @Test
    fun firstWindowHasNoContextAndTheNextOnesCarryTheTailOfThePreviousWindow() {
        val aggregator = aggregator()

        val first = aggregator.onFrame(frameOf(100L, 1)).single()
        val second = aggregator.onFrame(frameOf(100L, 2)).single()

        assertEquals(0, first.contextPcm.size, "a primeira janela não tem áudio anterior")
        assertEquals(0L, first.contextDurationMs)
        assertEquals(CONTEXT_MS, second.contextDurationMs)
        assertContentEquals(ByteArray(CONTEXT_BYTES) { 1 }, second.contextPcm, "o contexto é o fim da janela 0")
    }

    @Test
    fun contextIsTheTailOfTheStreamEvenWhenThePreviousWindowIsShorterThanTheContext() {
        // Janela 1 dura 60 ms, menos que os 80 ms de contexto: o resto vem da janela 0.
        val aggregator = DictationWindowAggregator(targetDurationMs = 60L, contextDurationMs = CONTEXT_MS)

        aggregator.onFrame(frameOf(60L, 1)).single()
        aggregator.onFrame(frameOf(60L, 2)).single()
        val third = aggregator.onFrame(frameOf(60L, 3)).single()

        assertEquals(CONTEXT_MS, third.contextDurationMs)
        assertContentEquals(
            ByteArray(CONTEXT_BYTES - bytes(60L)) { 1 } + ByteArray(bytes(60L)) { 2 },
            third.contextPcm,
            "o contexto atravessa a fronteira da janela anterior"
        )
    }

    @Test
    fun contextIsNotPartOfThePcmSoNoByteIsCountedTwiceAndTheTimelineIsKept() {
        val aggregator = aggregator()
        val frames = listOf(frameOf(100L, 1), frameOf(100L, 2), frameOf(50L, 3))

        val windows = frames.flatMap { aggregator.onFrame(it) } + listOfNotNull(aggregator.flush())

        assertContentEquals(
            frames.map { it.pcm }.reduce { acc, next -> acc + next },
            windows.map { it.pcm }.reduce { acc, next -> acc + next },
            "o áudio próprio das janelas continua sendo a captura inteira, sem repetir o contexto"
        )
        assertEquals(listOf(0L, 100L, 200L), windows.map { it.startedAtMs })
        assertEquals(listOf(100L, 200L, 250L), windows.map { it.finishedAtMs })
        windows.forEach { assertEquals(it.finishedAtMs - it.startedAtMs, it.durationMs) }
    }

    @Test
    fun theAudioSentToTheModelIsTheContextFollowedByTheWindowAudio() {
        val aggregator = aggregator()

        aggregator.onFrame(frameOf(100L, 1))
        val second = aggregator.onFrame(frameOf(100L, 2)).single()

        assertContentEquals(
            ByteArray(CONTEXT_BYTES) { 1 } + ByteArray(bytes(100L)) { 2 },
            second.transmittedPcm
        )
        assertEquals(CONTEXT_MS + 100L, second.transmittedDurationMs)
    }

    @Test
    fun withoutContextTheTransmittedAudioIsTheWindowAudioItself() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        aggregator.onFrame(frameOf(100L, 1))
        val second = aggregator.onFrame(frameOf(100L, 2)).single()

        assertEquals(0, second.contextPcm.size)
        assertContentEquals(second.pcm, second.transmittedPcm)
        assertEquals(second.durationMs, second.transmittedDurationMs)
    }

    @Test
    fun theFinalFlushWindowAlsoCarriesContext() {
        val aggregator = aggregator()

        aggregator.onFrame(frameOf(100L, 1))
        aggregator.onFrame(frameOf(50L, 2))
        val tail = aggregator.flush()

        assertNotNull(tail)
        assertEquals(CONTEXT_MS, tail.contextDurationMs)
        assertContentEquals(ByteArray(CONTEXT_BYTES) { 1 }, tail.contextPcm)
    }

    // P124: uma captura silenciada pelo sistema chega zerada. Se o contexto entrasse no `pcm`, a fala
    // da janela anterior faria a janela muda parecer sonora, custando uma requisição e podendo voltar
    // como texto inventado.
    @Test
    fun aDigitallySilentWindowIsStillDetectedAsSilentEvenWithSpokenContext() {
        val aggregator = aggregator()

        aggregator.onFrame(frameOf(100L, 1))
        val silent = aggregator.onFrame(frameOf(100L, 0)).single()

        assertTrue(silent.contextPcm.any { it != ZERO }, "o contexto tem a fala da janela anterior")
        assertTrue(SilentWindow.detect(silent), "a janela muda tem de continuar sendo silêncio digital")
    }

    @Test
    fun clearForgetsTheContextOfThePreviousSession() {
        val aggregator = aggregator()

        aggregator.onFrame(frameOf(100L, 1))
        aggregator.clear()
        val first = aggregator.onFrame(frameOf(100L, 2)).single()

        assertEquals(0, first.contextPcm.size, "a sessão nova começa sem áudio da anterior")
    }

    @Test
    fun negativeContextIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            DictationWindowAggregator(targetDurationMs = 100L, contextDurationMs = -1L)
        }
    }

    private fun bytes(ms: Long): Int = (ms * BYTES_PER_MS).toInt()

    private companion object {
        const val BYTES_PER_MS = 32L
        const val CONTEXT_MS = 80L
        const val CONTEXT_BYTES = (CONTEXT_MS * BYTES_PER_MS).toInt()
        const val ZERO: Byte = 0
    }
}
