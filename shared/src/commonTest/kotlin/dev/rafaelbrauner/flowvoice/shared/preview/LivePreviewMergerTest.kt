package dev.rafaelbrauner.flowvoice.shared.preview

import dev.rafaelbrauner.flowvoice.shared.pipeline.LiveDictationText
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class LivePreviewMergerTest {
    // O oráculo é a função pura de verdade: o esperado é apply(saida da função pura) campo a
    // campo. O fake marca todo texto que passou por ele (colchetes) e substitui uma palavra
    // ("casa" -> "casa#"), então qualquer divergência — apply faltando, aplicado duas vezes,
    // aplicado sobre o texto errado — aparece na comparação.
    @Test
    fun previewMatchesAssembleAcrossSessionStates() {
        val fake = FakeVocabulary()
        val merger = LivePreviewMerger(fake::apply, { 0L })
        for (state in sessionStates()) {
            val assembled = LivePreviewAssembler.assemble(state.segments, state.sessionComplete)
            val expected = LivePreview(fake.apply(assembled.finalized), fake.apply(assembled.provisional))
            assertEquals(expected, merger.preview(state.segments, state.sessionComplete), "preview divergiu em: ${state.name}")
        }
    }

    // Mesma sequência pela via do motor no aparelho, com o parcial mudando a cada estado: o
    // esperado é o que o liveText() de hoje produz — apply(stable) e apply(tail juntado com o
    // parcial).
    @Test
    fun liveMatchesSplitAcrossSessionStates() {
        val fake = FakeVocabulary()
        val merger = LivePreviewMerger(fake::apply, { 0L })
        for (state in sessionStates()) {
            val split = LiveDictationText.split(state.segments)
            val expected = LivePreview(
                finalized = fake.apply(split.finalized),
                provisional = fake.apply(withPartial(split.provisional, state.partial))
            )
            assertEquals(expected, merger.live(state.segments, state.partial), "live divergiu em: ${state.name}")
        }
    }

    // Entre ticks de parcial o stable não muda: o memo o mantém e só o tail com parcial é
    // aplicado de novo — 1 chamada por tick, não o texto inteiro.
    @Test
    fun partialTickReappliesOnlyTheTail() {
        val fake = FakeVocabulary()
        val merger = LivePreviewMerger(fake::apply, { 0L })
        val segments = listOf(
            ok(0, "entrei na casa amarela"),
            ok(1, "Casa amarela ficou pronta.", contextDurationMs = CONTEXT_MS)
        )
        merger.live(segments, "entrei na")
        val warm = fake.calls
        val tick = merger.live(segments, "entrei na casa")
        assertEquals(warm + 1, fake.calls, "o stable não pode reaplicar entre ticks de parcial")
        val split = LiveDictationText.split(segments)
        // O oráculo usa um fake próprio: apply dos valores esperados não pode contamizar o
        // contador do memo, que é justamente o que este teste mede.
        val oracle = FakeVocabulary()
        assertEquals(
            oracle.apply(withPartial(split.provisional, "entrei na casa")),
            tick.provisional,
            "o parcial novo tem de aparecer aplicado no tail"
        )
        assertEquals(oracle.apply(split.finalized), tick.finalized)
        merger.live(segments, "entrei na casa amarela")
        assertEquals(warm + 2, fake.calls, "cada tick de parcial custa exatamente 1 apply")
    }

    // Sem mudança de segmentos, preview não paga apply nenhum; um apêndice paga só o novo
    // provisional (o finalized "" continua em memo).
    @Test
    fun previewReappliesOnlyWhenTheMergeChanges() {
        val fake = FakeVocabulary()
        val merger = LivePreviewMerger(fake::apply, { 0L })
        val segments = listOf(ok(0, "entrei na casa amarela"))
        merger.preview(segments, false)
        val warm = fake.calls
        merger.preview(segments, false)
        assertEquals(warm, fake.calls, "chamada repetida sem mudança não pode pagar apply")
        val appended = segments + listOf(ok(1, "Casa amarela ficou pronta.", contextDurationMs = CONTEXT_MS))
        merger.preview(appended, false)
        assertEquals(warm + 1, fake.calls, "o apêndice paga só o novo texto acumulado")
    }

    // O flip de sessionComplete muda só o empacotamento: o merge não muda, e os textos de ambos
    // os empacotamentos já estão em memo depois da primeira passagem.
    @Test
    fun sessionCompleteFlipReusesMergeAndVocabularyMemo() {
        val fake = FakeVocabulary()
        val merger = LivePreviewMerger(fake::apply, { 0L })
        val segments = listOf(
            ok(0, "entrei na casa amarela"),
            ok(1, "Casa amarela ficou pronta.", contextDurationMs = CONTEXT_MS)
        )
        merger.preview(segments, false)
        merger.preview(segments, true)
        val warm = fake.calls
        merger.preview(segments, false)
        merger.preview(segments, true)
        merger.preview(segments, false)
        assertEquals(warm, fake.calls, "flip de sessionComplete não pode pagar apply novo")
    }

    // revision() mudou: o vocabulário mudou, o memo do apply invalida e os textos reaplicam. Cada
    // via usa o próprio merger e o próprio contador, para a contagem não depender de colisões de
    // texto entre vias no memo compartilhado.
    @Test
    fun vocabularyRevisionChangeReappliesText() {
        val segments = listOf(
            ok(0, "entrei na casa amarela"),
            ok(1, "Casa amarela ficou pronta.", contextDurationMs = CONTEXT_MS)
        )
        var revision = 0L
        val liveFake = FakeVocabulary()
        val liveMerger = LivePreviewMerger(liveFake::apply, { revision })
        liveMerger.live(segments, "parcial")
        liveMerger.live(segments, "parcial")
        liveMerger.live(segments, "outro parcial")
        val liveWarm = liveFake.calls
        revision = 1L
        liveMerger.live(segments, "outro parcial")
        // Estado firme é 1 apply por tick (só o tail); com revision novo o stable reaplica junto: 2.
        assertEquals(liveWarm + 2, liveFake.calls, "revision mudou: o stable tem de reaplicar")

        val previewFake = FakeVocabulary()
        val previewMerger = LivePreviewMerger(previewFake::apply, { revision })
        previewMerger.preview(segments, false)
        previewMerger.preview(segments, false)
        val previewWarm = previewFake.calls
        revision = 2L
        previewMerger.preview(segments, false)
        assertEquals(previewWarm + 2, previewFake.calls, "revision mudou: finalized e provisional reaplicam")
    }

    // reset: nada sobra — os textos reaplicam e a saída continua a da função pura.
    @Test
    fun resetClearsMergeAndVocabularyMemo() {
        val fake = FakeVocabulary()
        val merger = LivePreviewMerger(fake::apply, { 0L })
        val segments = listOf(ok(0, "entrei na casa amarela"))
        merger.live(segments, "parcial")
        val warm = fake.calls
        merger.reset()
        val again = merger.live(segments, "parcial")
        assertEquals(warm + 2, fake.calls, "reset limpa o memo: stable e tail reaplicam")
        val split = LiveDictationText.split(segments)
        val expected = LivePreview(
            finalized = fake.apply(split.finalized),
            provisional = fake.apply(withPartial(split.provisional, "parcial"))
        )
        assertEquals(expected, again, "depois do reset a saída continua idêntica à pura")
    }

    private data class SessionState(
        val name: String,
        val segments: List<TranscriptionSegment>,
        val sessionComplete: Boolean,
        val partial: String
    )

    // Estados de uma sessão real, na ordem em que o pipeline os vê: apêndice, retry do último,
    // mudança no meio, chegada fora de ordem, contexto zero, falha de janela, fluxo contínuo do
    // motor local, pedaço só de '\n' (entra na via da nuvem, não na do motor), janela
    // transcrevendo, flip de sessionComplete e volta a transmitir.
    private fun sessionStates() = listOf(
        SessionState("sessão vazia", emptyList(), false, ""),
        SessionState("primeira janela", listOf(ok(0, "entrei na casa amarela")), false, "entrei na"),
        // P143: a janela seguinte repete o fim da anterior e o contexto autoriza a deduplicação.
        SessionState(
            "apêndice com contexto",
            listOf(ok(0, "entrei na casa amarela"), ok(1, "Casa amarela ficou pronta.", contextDurationMs = CONTEXT_MS)),
            false,
            "ficou"
        ),
        SessionState(
            "retry do último",
            listOf(ok(0, "entrei na casa amarela"), ok(1, "a casa amarela ficou pronta hoje.", contextDurationMs = CONTEXT_MS)),
            false,
            "pronta hoje"
        ),
        SessionState(
            "mudança no meio",
            listOf(ok(0, "entrei na casa azul"), ok(1, "a casa amarela ficou pronta hoje.", contextDurationMs = CONTEXT_MS)),
            false,
            "hoje"
        ),
        SessionState(
            "chegada fora de ordem",
            listOf(ok(1, "a casa amarela ficou pronta hoje.", contextDurationMs = CONTEXT_MS), ok(0, "entrei na casa azul")),
            false,
            "casa"
        ),
        // P127: sem contexto, repetição de palavra é o usuário repetindo e entra inteira.
        SessionState(
            "apêndice sem contexto",
            listOf(
                ok(0, "entrei na casa azul"),
                ok(1, "a casa amarela ficou pronta hoje.", contextDurationMs = CONTEXT_MS),
                ok(2, "casa fechada.")
            ),
            false,
            "fechada"
        ),
        SessionState(
            "janela falhou",
            listOf(ok(0, "entrei na casa azul"), failed(1), ok(2, "casa fechada.")),
            false,
            ""
        ),
        SessionState(
            "fluxo contínuo",
            listOf(
                ok(0, "entrei na casa azul"),
                ok(2, "casa fechada."),
                ok(3, "que ficou de pé", continuous = true),
                ok(4, "ada de família.", continuous = true, glued = true)
            ),
            false,
            "de família"
        ),
        SessionState(
            "pedaço só de linha nova",
            listOf(
                ok(0, "entrei na casa azul"),
                ok(2, "casa fechada."),
                ok(3, "que ficou de pé", continuous = true),
                ok(4, "ada de família.", continuous = true, glued = true),
                ok(5, "\n")
            ),
            false,
            ""
        ),
        SessionState(
            "janela transcrevendo",
            listOf(
                ok(0, "entrei na casa azul"),
                ok(2, "casa fechada."),
                ok(3, "que ficou de pé", continuous = true),
                ok(4, "ada de família.", continuous = true, glued = true),
                ok(5, "\n"),
                transcribing(6)
            ),
            false,
            "linha"
        ),
        SessionState(
            "sessão completa",
            listOf(
                ok(0, "entrei na casa azul"),
                ok(2, "casa fechada."),
                ok(3, "que ficou de pé", continuous = true),
                ok(4, "ada de família.", continuous = true, glued = true),
                ok(5, "\n")
            ),
            true,
            ""
        ),
        // sessionComplete com janela ainda transcrevendo: assemble guarda com && !transcribing e
        // mantém tudo provisório, com o marcador de pendência.
        SessionState(
            "completo com janela transcrevendo",
            listOf(
                ok(0, "entrei na casa azul"),
                ok(2, "casa fechada."),
                ok(3, "que ficou de pé", continuous = true),
                ok(4, "ada de família.", continuous = true, glued = true),
                ok(5, "\n"),
                transcribing(6)
            ),
            true,
            "mais um pedaço"
        )
    )

    // Igual ao withPartial do DictationPipeline: o parcial entra na ponta do provisório.
    private fun withPartial(provisional: String, partial: String): String =
        listOf(provisional, partial).filter { it.isNotBlank() }.joinToString(" ")

    private fun ok(
        index: Int,
        text: String,
        contextDurationMs: Long = 0L,
        continuous: Boolean = false,
        glued: Boolean = false
    ) = TranscriptionSegment(
        index,
        TranscriptionSegment.Status.Ok,
        text,
        contextDurationMs = contextDurationMs,
        continuous = continuous,
        glued = glued
    )

    private fun failed(index: Int) =
        TranscriptionSegment(index, TranscriptionSegment.Status.Failed, errorKind = "network")

    private fun transcribing(index: Int) =
        TranscriptionSegment(index, TranscriptionSegment.Status.Transcribing)

    private class FakeVocabulary {
        var calls = 0
            private set

        fun apply(text: String): String {
            calls++
            return "[" + text.replace("casa", "casa#") + "]"
        }
    }

    private companion object {
        const val CONTEXT_MS = 1_000L
    }
}