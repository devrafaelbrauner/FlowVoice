package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.test.Test
import kotlin.test.assertEquals

// P146: o contexto sobreposto da P143 é cortado pelo **tempo**, não pelo texto. Tudo que termina
// antes do fim do contexto é repetição e sai; o que sobra é a fala da janela, mesmo que o modelo
// tenha transcrito o contexto com outras palavras ("Avaliado pelo doutor" → "Segundo doutor").
//
// A folga (`TOLERANCE_MS`) empurra o corte para trás, nunca para a frente: na dúvida a palavra fica
// e a deduplicação por texto (P127/P143) a remove depois. Perder a primeira palavra da janela seria
// irreversível; repeti-la, não.
class ContextTrimTest {
    @Test
    fun wordsEndingInsideTheContextAreDropped() {
        val payload = TranscriptionPayload(
            text = "Segundo doutor Grandmont.",
            words = listOf(
                TimedUnit("Segundo", 100L, 500L),
                TimedUnit("doutor", 500L, 860L),
                TimedUnit("Grandmont", 1_100L, 1_600L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("Grandmont.", result.text, "o contexto retranscrito sai pelo tempo")
        assertEquals(ContextTrim.Strategy.Words, result.strategy)
        assertEquals(860L, result.droppedMs)
        assertEquals(2, result.droppedWords)
    }

    // O texto sobrevivente sai do `text` original, não da junção das palavras: assim a pontuação e a
    // caixa do modelo ficam intactas, mesmo que `words` venha sem pontuação.
    @Test
    fun theKeptTextPreservesPunctuationAndCase() {
        val payload = TranscriptionPayload(
            text = "com diarreia, há três dias.",
            words = listOf(
                TimedUnit("com", 200L, 500L),
                TimedUnit("diarreia", 500L, 800L),
                TimedUnit("há", 1_200L, 1_300L),
                TimedUnit("três", 1_300L, 1_500L),
                TimedUnit("dias", 1_500L, 1_800L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("há três dias.", result.text)
    }

    @Test
    fun withoutWordsTheCutIsBySegment() {
        val payload = TranscriptionPayload(
            text = "Avaliado pelo doutor. Grandmont chegou.",
            segments = listOf(
                TimedUnit("Avaliado pelo doutor.", 0L, 850L),
                TimedUnit("Grandmont chegou.", 1_000L, 2_400L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("Grandmont chegou.", result.text)
        assertEquals(ContextTrim.Strategy.Segments, result.strategy)
        assertEquals(850L, result.droppedMs)
        assertEquals(1, result.droppedSegments)
        assertEquals(0, result.droppedWords, "no corte por segmento não se conta palavra")
    }

    // Provedor que aceita `verbose_json` mas não devolve tempos: nada é cortado aqui e a rede de
    // segurança por texto continua valendo a jusante.
    @Test
    fun withoutTimesNothingIsCutAndTheTextComesWhole() {
        val payload = TranscriptionPayload(text = "Segundo doutor Grandmont.")

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("Segundo doutor Grandmont.", result.text)
        assertEquals(ContextTrim.Strategy.Text, result.strategy)
        assertEquals(0L, result.droppedMs)
    }

    // Janela 0 (e qualquer outra sem contexto): não há repetição para tirar.
    @Test
    fun aWindowWithoutContextKeepsEveryWord() {
        val payload = TranscriptionPayload(
            text = "Avaliado pelo doutor.",
            words = listOf(
                TimedUnit("Avaliado", 0L, 400L),
                TimedUnit("pelo", 400L, 700L),
                TimedUnit("doutor", 700L, 1_100L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 0L)

        assertEquals("Avaliado pelo doutor.", result.text)
        assertEquals(ContextTrim.Strategy.Text, result.strategy)
        assertEquals(0, result.droppedWords)
    }

    @Test
    fun aWordStartingExactlyAtTheBorderIsKept() {
        val payload = TranscriptionPayload(
            text = "doutor Grandmont.",
            words = listOf(
                TimedUnit("doutor", 300L, 700L),
                TimedUnit("Grandmont", 1_000L, 1_600L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("Grandmont.", result.text)
        assertEquals(1, result.droppedWords)
    }

    // A folga faz a palavra que **termina** na fronteira ficar: ela pode ser a primeira da janela,
    // atrasada por arredondamento. Se for mesmo o contexto, a deduplicação por texto a remove.
    @Test
    fun aWordEndingExactlyAtTheBorderIsKeptByTheTolerance() {
        val payload = TranscriptionPayload(
            text = "doutor Grandmont.",
            words = listOf(
                TimedUnit("doutor", 600L, 1_000L),
                TimedUnit("Grandmont", 1_100L, 1_600L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("doutor Grandmont.", result.text)
        assertEquals(0, result.droppedWords)
    }

    @Test
    fun aWindowWhoseWordsAllFallInsideTheContextBecomesEmpty() {
        val payload = TranscriptionPayload(
            text = "de losartana.",
            words = listOf(
                TimedUnit("de", 100L, 300L),
                TimedUnit("losartana", 300L, 800L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("", result.text)
        assertEquals(2, result.droppedWords)
        assertEquals(800L, result.droppedMs)
    }

    @Test
    fun emptyTextStaysEmpty() {
        val result = ContextTrim.trim(TranscriptionPayload(text = "   "), contextDurationMs = 1_000L)

        assertEquals("", result.text)
        assertEquals(0, result.droppedWords)
    }

    // Só o começo é contexto: uma palavra de tempo estranho no meio da janela não pode sumir.
    @Test
    fun onlyThePrefixIsDroppedEvenIfALaterWordHasABrokenTimestamp() {
        val payload = TranscriptionPayload(
            text = "doutor Grandmont chegou.",
            words = listOf(
                TimedUnit("doutor", 300L, 700L),
                TimedUnit("Grandmont", 1_100L, 1_600L),
                TimedUnit("chegou", 0L, 0L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("Grandmont chegou.", result.text)
        assertEquals(1, result.droppedWords)
    }

    // Quando a contagem de palavras não bate com a do texto (palavra hifenizada, pontuação solta), o
    // texto é remontado a partir das palavras que ficaram: perde-se pontuação, não palavra.
    @Test
    fun whenTheWordCountDoesNotMatchTheTextTheKeptWordsAreJoined() {
        val payload = TranscriptionPayload(
            text = "doutor Grandmont chegou.",
            words = listOf(
                TimedUnit("doutor", 300L, 700L),
                TimedUnit("Grand", 1_100L, 1_300L),
                TimedUnit("mont", 1_300L, 1_500L),
                TimedUnit("chegou", 1_500L, 1_900L)
            )
        )

        val result = ContextTrim.trim(payload, contextDurationMs = 1_000L)

        assertEquals("Grand mont chegou", result.text)
        assertEquals(1, result.droppedWords)
    }
}
