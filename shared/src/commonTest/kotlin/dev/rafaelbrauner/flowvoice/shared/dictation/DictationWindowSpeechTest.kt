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

    // Tom de nível constante: o nível de bloco do endpointer é a média do valor absoluto das
    // amostras de 16 bits, então uma amostra fixa vira exatamente esse nível.
    private fun tone(ms: Long, level: Int): ByteArray {
        val low = (level and 0xFF).toByte()
        val high = (level shr 8).toByte()
        return ByteArray(bytes(ms)) { if (it % 2 == 0) low else high }
    }

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
        assertEquals(
            WindowSpeechGate.REASON_ONLY_CONTEXT,
            WindowSpeechGate.skipReason(leading),
            "tem de ser pulada sem requisição"
        )
    }

    @Test
    fun aWindowWithSpeechKeepsItsVoicedTimeAndItsContext() {
        // Duas frases com pausa entre elas: as duas janelas têm fala, e a segunda leva o contexto.
        val windows = feed(aggregator(), speech(1_000L) + silence(700L) + speech(1_000L) + silence(700L))

        assertTrue(windows.size >= 2, "esperadas duas janelas de pausa, veio ${windows.size}")
        windows.forEach {
            assertNotNull(it.voicedMs)
            assertTrue(it.voicedMs!! > 0L, "janela ${it.index} com voicedMs ${it.voicedMs}")
            assertNull(WindowSpeechGate.skipReason(it), "janela ${it.index} com fala não pode ser pulada")
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
        assertEquals(
            WindowSpeechGate.REASON_ONLY_CONTEXT,
            WindowSpeechGate.skipReason(tail),
            "o flush só de silêncio tem de ser pulado"
        )
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
        assertNull(WindowSpeechGate.skipReason(tail))
    }

    // Sem endpointing não há piso de ruído nem limiar de fala: `voicedMs` fica desconhecido e nada é
    // pulado. É o que protege quem fala baixo de perder o ditado inteiro.
    @Test
    fun withoutEndpointingTheVoicedTimeIsUnknownAndNothingIsSkipped() {
        val aggregator = DictationWindowAggregator(targetDurationMs = 100L)

        val window = aggregator.onFrame(AudioFrame(silence(100L))).single()

        assertNull(window.voicedMs)
        assertTrue(window.hasOwnSpeech, "desconhecido conta como tendo fala")
        assertNull(WindowSpeechGate.skipReason(window))
    }

    // P151: de onde vinham as janelas 6, 7 e 8 do S26 (2026-09-16 10:42), enviadas com
    // `durationMs=3180/3680/4180` e devolvidas com `chars=0`. Ruído de sala parado, sem contraste no
    // histórico: nenhum bloco fica abaixo do nível de pausa, então não há pausa nem corte `leading`,
    // e a janela sai no teto — o corte que a P144 mandava enviar sempre.
    @Test
    fun steadyRoomNoiseBecomesACeilingWindowWithoutAnyMeasuredSpeech() {
        val window = feed(aggregator(), tone(4_400L, ROOM_NOISE_LEVEL)).single()

        assertEquals(WindowCut.Ceiling, window.cut)
        assertEquals(3_160L, window.durationMs, "a janela de silêncio medida no aparelho tinha 3180 ms")
        assertEquals(0L, window.voicedMs, "ruído parado não é fala")
        assertEquals(
            WindowSpeechGate.REASON_NOT_ENOUGH_SPEECH,
            WindowSpeechGate.skipReason(window),
            "não pode custar requisição"
        )
    }

    // A trava da P151 não pode engolir voz baixa: 400 ms de fala fraca (nível 300, contra ruído 200)
    // no meio do mesmo ruído da janela acima continuam sendo enviados, no mesmo corte de teto.
    @Test
    fun aShortQuietUtteranceInsideTheSameNoiseIsStillSent() {
        val pcm = tone(1_500L, ROOM_NOISE_LEVEL) +
            tone(400L, QUIET_SPEECH_LEVEL) +
            tone(2_500L, ROOM_NOISE_LEVEL)

        val window = feed(aggregator(), pcm).single()

        assertEquals(WindowCut.Ceiling, window.cut)
        assertEquals(400L, window.voicedMs, "os 400 ms de voz baixa têm de ser medidos")
        assertNull(WindowSpeechGate.skipReason(window), "voz baixa continua indo à transcrição")
    }

    private companion object {
        const val BYTES_PER_MS = 32L
        const val FRAME_100_MS_BYTES = 3_200

        // Níveis da zona morta do endpointer no aparelho: acima do nível de pausa e abaixo do de
        // fala. O ruído fica aí parado; a voz baixa passa do limiar de fala (250 sem contraste).
        const val ROOM_NOISE_LEVEL = 200
        const val QUIET_SPEECH_LEVEL = 300
    }
}
