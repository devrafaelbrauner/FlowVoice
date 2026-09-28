package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommitChunksTest {

    // O texto final de um ditado de 20 s troca o rascunho numa escrita só: nenhum pedaço pode chegar aos 25
    // caracteres que o corpo de nota do Samsung Notes trata como colagem, e juntos têm de ser o texto inteiro.
    @Test
    fun everyChunkStaysBelowWhatTheNoteBodyTreatsAsPasteAndTogetherTheyAreTheText() {
        val text = "Paciente do leito doze segue com dispneia. Pedi uma radiografia de tórax. Ela está eupneica, " +
            "sem sinais de desconforto respiratório. O hemograma veio normal, só a glicemia um pouco alterada.\n" +
            "A tomografia mostrou uma broncopneumonia à direita. Solicitei transferência para a UTI."

        val chunks = CommitChunks.split(text)

        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length < 25 }, chunks.joinToString("|"))
    }

    @Test
    fun cutsBeforeASpaceSoEachChunkStartsLikeADictatedPiece() {
        assertEquals(
            listOf(" Consegue me ligar", " mais tarde?"),
            CommitChunks.split(" Consegue me ligar mais tarde?")
        )
    }

    @Test
    fun wordLongerThanAChunkIsCutWithoutSplittingAnEmoji() {
        val text = "a".repeat(19) + "😀" + "otorrinolaringologista"

        val chunks = CommitChunks.split(text)

        assertEquals(text, chunks.joinToString(""))
        assertEquals("a".repeat(19), chunks.first())
        assertTrue(chunks.all { it.length <= CommitChunks.MAX_CHARS && !it.first().isLowSurrogate() })
    }

    @Test
    fun shortTextIsOneCommitAndEmptyTextIsNone() {
        assertEquals(listOf(" Paciente do leito doze."), CommitChunks.split(" Paciente do leito doze.", max = 24))
        assertEquals(emptyList(), CommitChunks.split(""))
    }
}
