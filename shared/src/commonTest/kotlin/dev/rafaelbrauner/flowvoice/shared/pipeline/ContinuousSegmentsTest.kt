package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlin.test.Test
import kotlin.test.assertEquals

// Pedaços do fluxo contínuo (motor no aparelho) pelos três caminhos do texto: digitação direta,
// texto final (revisão e nota) e prévia ao vivo. Nada se repete entre eles, e o que o fluxo diz que
// continua a palavra anterior entra colado.
class ContinuousSegmentsTest {
    private val pieces = listOf(
        continuous(0, "chego em uns quarenta minuto"),
        continuous(1, "s", glued = true),
        continuous(2, "muito muito obrigado"),
        continuous(3, "muito", glued = false),
        continuous(4, ".", glued = true)
    )
    private val expected = "chego em uns quarenta minutos muito muito obrigado muito."

    @Test
    fun directTypingGluesContinuationsAndNeverDropsARepeatedWord() {
        val step = DirectInsertionPlanner.advance(DirectInsertionPlan(), pieces)

        assertEquals(expected, step.plan.transcript)
        assertEquals(listOf("", "", " ", " ", ""), step.pieces.map { it.separator })
    }

    @Test
    fun theFinalTextAndTheLivePreviewMatchWhatIsTyped() {
        assertEquals(expected, LivePreviewAssembler.assemble(pieces, sessionComplete = true).finalized)
        val live = LiveDictationText.split(pieces)
        assertEquals("chego em uns quarenta minutos muito muito obrigado muito", live.finalized)
        assertEquals(".", live.provisional)
    }

    @Test
    fun theSameTextFromTheCloudIsStillDeduplicatedAtTheSeam() {
        val cloud = pieces.take(3).map { it.copy(continuous = false, glued = false) }

        assertEquals(
            "chego em uns quarenta minuto s muito muito obrigado",
            LivePreviewAssembler.assemble(cloud, sessionComplete = true).finalized,
            "a regra da nuvem continua a mesma: é por isso que o pedaço contínuo não passa por ela"
        )
    }

    private fun continuous(index: Int, text: String, glued: Boolean = false) = TranscriptionSegment(
        windowIndex = index,
        status = TranscriptionSegment.Status.Ok,
        text = text,
        continuous = true,
        glued = glued
    )
}
