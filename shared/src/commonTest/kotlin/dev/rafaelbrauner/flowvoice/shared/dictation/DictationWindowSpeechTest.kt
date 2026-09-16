package dev.rafaelbrauner.flowvoice.shared.dictation

import dev.rafaelbrauner.flowvoice.shared.transcription.voicedPcm
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Fala própria por janela (P144). Uma janela cortada como `leading` (ou o `flush` do fim) pode conter
// só silêncio mais o contexto sobreposto da P143; o modelo então transcreve **o contexto** e o texto
// entra no campo. Medido no S26 (0.5.0 + P143, 2026-09-16 01:56): `window=3 text=3Gs.` (de "há três
// dias") e `window=16 text=Tô cansado.` repetindo o fim da frase anterior.
//
// `voicedMs` sai só do `pcm` próprio, com o mesmo limiar de fala do endpointer (relativo ao piso de
// ruído da sessão), nunca com valor fixo: fala baixa não pode virar silêncio (P124).
class DictationWindowSpeechTest {
    private fun bytes(ms: Long): Int = (ms * BYTES_PER_MS).toInt()

    private fun silence(ms: Long) = ByteArray(bytes(ms))

    private fun speech(ms: Long) = voicedPcm(bytes(ms))

    private fun aggregator() = DictationWindowAggregator(
        pauseSearchBeforeMs = DictationWindowAggregator.SPEECH_PAUSE_SEARCH_BEFORE_MS,
        pauseSearchAfterMs = DictationWindowAggregator.SPEECH_PAUSE_SEARCH_AFTER_MS,
        endpointing = SpeechEndpointing(),
        contextDurationMs = DictationWindowAggregator.SPEECH_CONTEXT_MS
    )

    private fun feed(aggregator: DictationWindowAggregator, pcm: ByteArray): List<DictationWindow> {
        val windows = mutableListOf<DictationWindow>()
        var fed = 0
        while (fed < pcm.size) {
            val end = min(pcm.size, fed + FRAME_100_MS_BYTES)
            windows += aggregator.onFrame(AudioFrame(pcm.copyOfRange(fed, end)))
            fed = end
        }
        return windows
    }

    @Test
    fun aWindowCutBeforeSpeechHasNoSpeechOfItsOwnAndCarriesNoContext() {
        // Fala, pausa (janela 0), um trecho longo sem fala e a fala seguinte: a janela do meio sai
        // como `leading` e só tem silêncio.
        val windows = feed(aggregator(), speech(1_000L) + silence(3_200L) + speech(2_500L))

        val leading = windows.single { it.cut == WindowCut.Leading }
        assertEquals(0L, leading.voicedMs, "a janela `leading` não tem fala própria")
        assertFalse(leading.hasOwnSpeech)
        assertEquals(0, leading.contextPcm.size, "janela sem fala não precisa levar contexto")
        assertTrue(leading.onlyContext, "tem de ser pulada sem requisição")
    }

    @Test
    fun aWindowWithSpeechKeepsItsVoicedTimeAndItsContext() {
        // Duas frases com pausa entre elas: as duas janelas têm fala, e a segunda leva o contexto.
        val windows = feed(aggregator(), speech(1_000L) + silence(700L) + speech(1_000L) + silence(700L))

        assertTrue(windows.size >= 2, "esperadas duas janelas de pausa, veio ${windows.size}")
        windows.forEach {
            assertNotNull(it.voicedMs)
            assertTrue(it.voicedMs!! > 0L, "janela ${it.index} com voicedMs ${it.voicedMs}")
            assertFalse(it.onlyContext, "janela ${it.index} com fala não pode ser pulada")
        }
        val second = windows.first { it.index > 0 }
        assertTrue(second.contextPcm.isNotEmpty(), "janela com fala continua levando o contexto da P143")
    }

    @Test
    fun theFinalFlushWithOnlySilenceHasNoSpeechOfItsOwn() {
        // A pausa precisa cruzar os 2 s mínimos da P143 para a janela 0 sair; só então sobra
        // silêncio puro para o flush.
        val aggregator = aggregator()
        feed(aggregator, speech(1_000L) + silence(1_200L))

        val tail = aggregator.flush()

        assertNotNull(tail)
        assertEquals(WindowCut.Flush, tail.cut)
        assertEquals(0L, tail.voicedMs)
        assertTrue(tail.onlyContext, "o flush só de silêncio tem de ser pulado")
        assertEquals(0, tail.contextPcm.size)
    }

    @Test
    fun theFinalFlushThatStillCarriesSpeechIsKept() {
        val aggregator = aggregator()
        feed(aggregator, speech(1_000L) + silence(700L) + speech(600L))

        val tail = aggregator.flush()

        assertNotNull(tail)
        assertEquals(WindowCut.Flush, tail.cut)
        assertTrue(tail.voicedMs!! > 0L, "o flush com a última palavra não pode ser pulado")
        assertFalse(tail.onlyContext)
    }

    // Sem endpointing não há piso de ruído nem limiar de fala: `voicedMs` fica desconhecido e nada é
    // pulado. É o que protege quem fala baixo de perder o ditado inteiro.
    @Test
    fun withoutEndpointingTheVoicedTimeIsUnknownAndNothingIsSkipped() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        val window = aggregator.onFrame(AudioFrame(silence(100L))).single()

        assertNull(window.voicedMs)
        assertTrue(window.hasOwnSpeech, "desconhecido conta como tendo fala")
        assertFalse(window.onlyContext)
    }

    private companion object {
        const val BYTES_PER_MS = 32L
        const val FRAME_100_MS_BYTES = 3_200
    }
}
