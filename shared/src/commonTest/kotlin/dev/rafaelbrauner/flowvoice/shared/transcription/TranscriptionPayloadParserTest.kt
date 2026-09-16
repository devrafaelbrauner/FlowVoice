package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// P146: o mesmo parser lê a resposta `json` e a `verbose_json`. Os tempos vêm em segundos e viram
// milissegundos; o que vier sem início ou sem fim é descartado, porque não dá para cortar por ele.
class TranscriptionPayloadParserTest {
    @Test
    fun readsWordsAndSegmentsFromVerboseJson() {
        val raw = """
            {
              "task": "transcribe",
              "language": "portuguese",
              "duration": 2.4,
              "text": "Segundo doutor Grandmont.",
              "words": [
                {"word": "Segundo", "start": 0.1, "end": 0.5},
                {"word": "doutor", "start": 0.5, "end": 0.86},
                {"word": "Grandmont", "start": 1.1, "end": 1.6}
              ],
              "segments": [
                {"id": 0, "start": 0.1, "end": 1.6, "text": "Segundo doutor Grandmont."}
              ],
              "usage": {"cost": 0.0011}
            }
        """.trimIndent()

        val payload = TranscriptionPayloadParser.parse(raw)!!

        assertEquals("Segundo doutor Grandmont.", payload.text)
        assertEquals(3, payload.words.size)
        assertEquals(TimedUnit("Segundo", 100L, 500L), payload.words.first())
        assertEquals(860L, payload.words[1].endMs)
        assertEquals(1, payload.segments.size)
        assertEquals(1_600L, payload.segments.first().endMs)
        assertEquals(0.0011, payload.costUsd)
        assertTrue(payload.hasTimes)
    }

    @Test
    fun readsThePlainJsonAnswerWithoutTimes() {
        val payload = TranscriptionPayloadParser.parse("""{"text":"olá mundo","usage":{"cost":0.002}}""")!!

        assertEquals("olá mundo", payload.text)
        assertEquals(0.002, payload.costUsd)
        assertFalse(payload.hasTimes)
    }

    @Test
    fun wordsWithoutTimesAreIgnored() {
        val raw = """{"text":"olá mundo","words":[{"word":"olá"},{"word":"mundo","start":0.4,"end":0.9}]}"""

        val payload = TranscriptionPayloadParser.parse(raw)!!

        assertEquals(listOf(TimedUnit("mundo", 400L, 900L)), payload.words)
    }

    @Test
    fun anAnswerWithoutTextBecomesEmptyText() {
        val payload = TranscriptionPayloadParser.parse("""{"usage":{"cost":0.001}}""")!!

        assertEquals("", payload.text)
    }

    @Test
    fun unparseableJsonIsNull() {
        assertNull(TranscriptionPayloadParser.parse("<html>502</html>"))
    }
}
