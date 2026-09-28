package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.WindowCut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinalPassAudioTest {
    // Janelas de 4 s (as da nuvem); cada byte do PCM é o índice da janela, para conferir a ordem.
    private fun windows(cuts: List<WindowCut>, seconds: Int = 4): List<DictationWindow> =
        cuts.mapIndexed { index, cut ->
            DictationWindow(
                index = index,
                pcm = ByteArray(seconds * BYTES_PER_SECOND) { index.toByte() },
                format = AudioFormat.DEFAULT,
                startedAtMs = index * seconds * 1_000L,
                finishedAtMs = (index + 1) * seconds * 1_000L,
                cut = cut
            )
        }

    @Test
    fun aDictationUnderTheLimitGoesWholeInOnePiece() {
        val chunks = FinalPassAudio.chunks(windows(List(12) { WindowCut.Target }))

        assertEquals(listOf(48_000L), chunks.map { it.durationMs })
        assertEquals((0 until 12).flatMap { i -> List(4 * BYTES_PER_SECOND) { i.toByte() } }, chunks.single().pcm.toList())
    }

    // 60 s com pausas aos 16 s, 36 s e 56 s: o corte fica na última pausa que deixa o pedaço com ao
    // menos 25 s (36 s), e não no teto de 48 s no meio da fala.
    @Test
    fun aLongDictationIsCutAtTheLastPauseInTheSecondHalfOfThePiece() {
        val cuts = List(15) { i -> if (i == 3 || i == 8 || i == 13) WindowCut.Pause else WindowCut.Ceiling }

        val chunks = FinalPassAudio.chunks(windows(cuts))

        assertEquals(listOf(36_000L, 24_000L), chunks.map { it.durationMs })
        assertEquals(8.toByte(), chunks[0].pcm.last())
        assertEquals(9.toByte(), chunks[1].pcm.first())
    }

    @Test
    fun speechResumingAfterSilenceAlsoCountsAsAQuietCut() {
        val cuts = List(15) { i -> if (i == 10) WindowCut.Leading else WindowCut.Target }

        assertEquals(listOf(44_000L, 16_000L), FinalPassAudio.chunks(windows(cuts)).map { it.durationMs })
    }

    // Sem pausa na segunda metade, o pedaço vai até o último fim de janela que cabe.
    @Test
    fun withoutAPauseThePieceEndsAtTheLastWindowThatFits() {
        val cuts = List(30) { i -> if (i == 2) WindowCut.Pause else WindowCut.Ceiling }

        val chunks = FinalPassAudio.chunks(windows(cuts))

        assertEquals(listOf(48_000L, 48_000L, 24_000L), chunks.map { it.durationMs })
        assertTrue(chunks.all { it.durationMs <= FinalPassAudio.MAX_CHUNK_MS })
    }

    @Test
    fun noAudioMeansNoPiece() {
        assertTrue(FinalPassAudio.chunks(emptyList()).isEmpty())
    }

    private companion object {
        const val BYTES_PER_SECOND = 16_000 * 2
    }
}
