package dev.rafaelbrauner.flowvoice.shared.localasr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDictationDriverTest {
    @Test
    fun partialsFollowTheAudioInOrderAndOnlyWhenTheTextChanges() = runTest {
        val engine = FakeSpeechEngine(
            emitted = mapOf(1 to listOf(" Bom"), 2 to listOf(" d", "ia"), 4 to listOf(","))
        )
        val run = DriverRun(this, engine)

        (1..4).forEach { run.driver.audio(chunk(it)) }
        runCurrent()

        assertEquals(listOf("Bom", "Bom dia", "Bom dia,"), run.partials)
    }

    @Test
    fun audioThatArrivesWhileTheModelLoadsIsTranscribedAfterIt() = runTest {
        val engine = FakeSpeechEngine(emitted = mapOf(1 to listOf(" Bom"), 2 to listOf(" dia")))
        val run = DriverRun(this, engine)

        // Nada rodou ainda na thread do motor: o áudio e o corte esperam na fila, como durante a carga.
        run.driver.audio(chunk(1))
        run.driver.audio(chunk(2))
        run.driver.cut(0, CommitRule.Finalize, last = true)
        runCurrent()

        assertEquals(listOf("start:pt-BR", "accept:1", "accept:2", "finish", "close"), engine.calls)
        assertEquals(listOf(0 to "Bom dia"), run.commits.map { it.first to it.second.text })
    }

    @Test
    fun aPauseCutFinalizesWithTheTailAndCommitsTheLastWord() = runTest {
        val engine = FakeSpeechEngine(
            emitted = mapOf(1 to listOf(" O", " exame")),
            heldUntilFinalize = mapOf(1 to listOf(" normal", "."))
        )
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.Finalize)
        runCurrent()

        assertEquals("O exame normal.", run.commits.single().second.text)
        assertEquals(CommitRule.Finalize, run.commits.single().second.rule)
        assertEquals("", run.partials.last())
    }

    @Test
    fun aCeilingCutInTheMiddleOfAWordKeepsTheWholeWordForTheNextPiece() = runTest {
        // Corte no teto com "d" emitido e "ia" ainda por vir: partir aqui daria "Bom d" + "ia".
        val engine = FakeSpeechEngine(
            emitted = mapOf(1 to listOf(" Bom", " d"), 2 to listOf("ia", " tudo")),
            heldUntilFinalize = mapOf(2 to listOf(" bem", "?"))
        )
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.WholeWords)
        run.driver.audio(chunk(2))
        run.driver.cut(1, CommitRule.Finalize, last = true)
        runCurrent()

        assertEquals(listOf("Bom", "dia tudo bem?"), run.commits.map { it.second.text })
        assertEquals("d", run.partials[run.partials.indexOf("Bom d") + 1], "o resto da palavra continua como parcial")
    }

    @Test
    fun theEndOfAWordThatComesOnlyAfterAPauseCutIsMarkedToGlueToThePreviousPiece() = runTest {
        // No S26 o "s" de "minutos" só saiu depois do fechamento da pausa: o pedaço seguinte começa no
        // meio da palavra e não pode ganhar espaço ("minuto s").
        val engine = FakeSpeechEngine(emitted = mapOf(1 to listOf(" quarenta", " minuto"), 2 to listOf("s", " vou")))
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.Finalize)
        run.driver.audio(chunk(2))
        run.driver.cut(1, CommitRule.Finalize, last = true)
        runCurrent()

        assertEquals(listOf("quarenta minuto" to false, "s vou" to true), run.commits.map { it.second.text to it.second.glued })
    }

    @Test
    fun theFirstPieceOfTheDictationNeverGlues() = runTest {
        val engine = FakeSpeechEngine(emitted = mapOf(1 to listOf("Bom", " dia", " ")))
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.WholeWords)
        runCurrent()

        assertEquals(false, run.commits.single().second.glued)
    }

    @Test
    fun aCeilingCutWithASingleUnfinishedWordCommitsNothing() = runTest {
        val engine = FakeSpeechEngine(emitted = mapOf(1 to listOf(" Leuco", "ci")))
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.WholeWords)
        runCurrent()

        assertEquals("", run.commits.single().second.text)
        assertEquals("Leucoci", run.partials.last())
    }

    @Test
    fun aStandaloneSpaceTokenClosesTheWordBeforeIt() = runTest {
        val engine = FakeSpeechEngine(emitted = mapOf(1 to listOf(" tudo", " bem", "?", " ", " ")))
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.WholeWords)
        runCurrent()

        assertEquals("tudo bem?", run.commits.single().second.text)
    }

    @Test
    fun stopFlushesTheLastUtteranceDeliversItAndClosesTheEngine() = runTest {
        val engine = FakeSpeechEngine(
            emitted = mapOf(1 to listOf(" Paciente", " estável")),
            heldUntilFinalize = mapOf(1 to listOf("."))
        )
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.WholeWords)
        run.driver.cut(1, CommitRule.Finalize, last = true)
        run.driver.audio(chunk(2))
        runCurrent()

        assertEquals(listOf("Paciente", "estável."), run.commits.map { it.second.text })
        assertEquals(1, engine.closed)
        assertTrue("accept:2" !in engine.calls, "nada entra depois do último corte")
        assertEquals(1, run.closed)
    }

    @Test
    fun anEngineFailureKeepsWhatWasRecognizedAndLaterCutsComeEmpty() = runTest {
        val engine = FakeSpeechEngine(
            emitted = mapOf(1 to listOf(" Pressão", " doze"), 3 to listOf(" por")),
            failOnChunk = 2
        )
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.audio(chunk(2))
        run.driver.audio(chunk(3))
        run.driver.cut(0, CommitRule.WholeWords)
        run.driver.cut(1, CommitRule.Finalize, last = true)
        runCurrent()

        assertEquals(1, run.failures.size)
        assertEquals(listOf("Pressão doze", ""), run.commits.map { it.second.text })
        assertTrue(run.commits.all { it.second.afterFailure })
        assertTrue("accept:3" !in engine.calls, "motor aposentado não recebe mais áudio")
        assertEquals(1, engine.closed)
    }

    @Test
    fun aFailureWhileFinalizingStillDeliversTheWordsAlreadyShown() = runTest {
        val engine = FakeSpeechEngine(
            emitted = mapOf(1 to listOf(" Sem", " queixas")),
            failOnFinalize = true
        )
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.Finalize, last = true)
        runCurrent()

        assertEquals("Sem queixas", run.commits.single().second.text)
        assertEquals(1, run.failures.size)
    }

    @Test
    fun aModelThatFailsToLoadReportsOnceAndEveryCutStillArrives() = runTest {
        val engine = FakeSpeechEngine(failOnStart = true)
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.WholeWords)
        run.driver.cut(1, CommitRule.Finalize, last = true)
        runCurrent()

        assertEquals(1, run.failures.size)
        assertEquals(listOf(0, 1), run.commits.map { it.first })
        assertTrue(run.commits.all { it.second.text.isEmpty() })
        assertEquals(0, run.readyCount)
    }

    @Test
    fun cancelDeliversNothingMoreAndClosesTheEngine() = runTest {
        val engine = FakeSpeechEngine(emitted = mapOf(1 to listOf(" texto")))
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        runCurrent()
        run.driver.cancel()
        run.driver.cut(0, CommitRule.Finalize, last = true)
        runCurrent()

        assertTrue(run.commits.isEmpty())
        assertEquals(1, engine.closed)
    }

    @Test
    fun doubleSpacesFromTheModelCollapseInTheCommittedText() = runTest {
        val engine = FakeSpeechEngine(emitted = mapOf(1 to listOf(" tudo", " bem?", "  ", " Consegue")))
        val run = DriverRun(this, engine)

        run.driver.audio(chunk(1))
        run.driver.cut(0, CommitRule.Finalize)
        runCurrent()

        assertEquals("tudo bem? Consegue", run.commits.single().second.text)
    }

    private class DriverRun(scope: TestScope, engine: StreamingSpeechEngine) : LocalDictationDriver.Listener {
        val partials = mutableListOf<String>()
        val commits = mutableListOf<Pair<Int, LocalCommit>>()
        val failures = mutableListOf<Throwable>()
        var readyCount = 0
        var closed = 0
        val driver = LocalDictationDriver(
            engine = engine,
            language = "pt-BR",
            scope = scope.backgroundScope as CoroutineScope,
            engineDispatcher = StandardTestDispatcher(scope.testScheduler),
            listener = this
        )

        override fun onReady(loadMs: Long) {
            readyCount++
        }

        override fun onPartial(text: String) {
            partials += text
        }

        override fun onCommit(windowIndex: Int, commit: LocalCommit) {
            commits += windowIndex to commit
        }

        override fun onFailure(error: Throwable) {
            failures += error
        }

        override fun onClosed(stats: LocalSessionStats) {
            closed++
        }
    }
}
