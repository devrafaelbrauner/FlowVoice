package dev.rafaelbrauner.flowvoice.shared.localasr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Como um corte do ditado fecha o pedaço no fluxo contínuo.
enum class CommitRule {
    // Pausa real ou fim do ditado: o motor recebe o silêncio de cauda e tudo o que foi dito sai.
    Finalize,
    // Corte sem pausa (teto): só as palavras inteiras; a última, que pode continuar, fica para depois.
    WholeWords
}

data class LocalCommit(
    val text: String,
    val rule: CommitRule,
    // Áudio que chegou ao motor desde o corte anterior.
    val audioMs: Long,
    // Tempo dentro do motor desde o corte anterior (accept, tokens, fechamento).
    val engineMs: Long,
    // Só o fechamento com cauda (Finalize): o que separa a pausa do texto.
    val finalizeMs: Long,
    // Do corte até o motor chegar a ele na fila: diz se o motor acompanha a fala.
    val waitMs: Long,
    // Parciais mostrados desde o corte anterior.
    val partials: Int,
    // O motor já tinha falhado: o texto é o que ele reconheceu até a falha.
    val afterFailure: Boolean
)

// Um ditado por um StreamingSpeechEngine, numa thread só (`engineDispatcher`): o áudio e os cortes
// entram numa fila na ordem da captura, e o motor os consome nessa ordem. Enquanto o modelo carrega
// em `start`, a fila cresce: o áudio de quem começou a falar antes do modelo ficar pronto não se
// perde. Um fluxo só por ditado; cada corte devolve o texto desde o corte anterior.
//
// Falha do motor: a primeira exceção o aposenta pelo resto do ditado (`onFailure` uma vez). O que ele
// já tinha reconhecido — o que já esteve na tela como parcial — sai no corte seguinte; os cortes
// depois disso saem vazios. Nenhum corte some: todo `cut` chega a `onCommit`, na ordem.
class LocalDictationDriver(
    private val engine: StreamingSpeechEngine,
    private val language: String,
    scope: CoroutineScope,
    engineDispatcher: CoroutineDispatcher,
    private val listener: Listener,
    private val timeSource: TimeSource = TimeSource.Monotonic
) {
    interface Listener {
        // Motor pronto: `loadMs` inclui a carga do modelo quando ela aconteceu.
        fun onReady(loadMs: Long)

        // Texto reconhecido desde o último corte, só quando muda. Provisório: nunca vai a um campo.
        fun onPartial(text: String)

        fun onCommit(windowIndex: Int, commit: LocalCommit)

        fun onFailure(error: Throwable)

        // O fluxo fechou (último corte ou cancelamento). Só números.
        fun onClosed(stats: LocalSessionStats) {}
    }

    private sealed interface Command {
        class Audio(val samples: FloatArray) : Command
        class Cut(val windowIndex: Int, val rule: CommitRule, val last: Boolean, val requestedAt: TimeMark) : Command
    }

    // Ilimitada de propósito: um envio que falha perderia fala, e suspender a captura perderia áudio.
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val job: Job = scope.launch(engineDispatcher) { consume() }

    // Mais um bloco da captura, PCM já em float. Não bloqueia.
    fun audio(samples: FloatArray) {
        commands.trySend(Command.Audio(samples))
    }

    // O ditado cortou aqui: tudo o que entrou por `audio` até agora é deste corte. `last` fecha o fluxo
    // depois de entregar.
    fun cut(windowIndex: Int, rule: CommitRule, last: Boolean = false) {
        commands.trySend(Command.Cut(windowIndex, rule, last, timeSource.markNow()))
    }

    // Larga o ditado: nada mais é entregue. O motor é fechado na thread dele.
    fun cancel() {
        commands.close()
        job.cancel()
    }

    suspend fun join() = job.join()

    private suspend fun consume() {
        var retired = false
        var tokens: List<String> = emptyList()
        var committed = 0
        var shown = ""
        var samples = 0L
        var engineTime = Duration.ZERO
        var partials = 0
        val stats = StatsCounter(timeSource)

        fun fail(error: Throwable) {
            if (retired) return
            retired = true
            listener.onFailure(error)
        }

        try {
            val loading = timeSource.markNow()
            try {
                engine.start(language)
                listener.onReady(loading.elapsedNow().inWholeMilliseconds)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(error)
            }

            for (command in commands) {
                when (command) {
                    is Command.Audio -> {
                        samples += command.samples.size
                        if (retired) continue
                        val started = timeSource.markNow()
                        try {
                            engine.accept(command.samples)
                            tokens = engine.tokens()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            fail(error)
                            continue
                        } finally {
                            engineTime += started.elapsedNow()
                        }
                        val partial = StreamingText.text(tokens, committed)
                        if (partial != shown) {
                            shown = partial
                            partials++
                            stats.partial()
                            listener.onPartial(partial)
                        }
                    }

                    is Command.Cut -> {
                        val waitMs = command.requestedAt.elapsedNow().inWholeMilliseconds
                        val started = timeSource.markNow()
                        var finalizeMs = 0L
                        val afterFailure = retired
                        val end = if (retired) {
                            tokens.size
                        } else {
                            try {
                                when (command.rule) {
                                    CommitRule.Finalize -> {
                                        engine.finishUtterance()
                                        tokens = engine.tokens()
                                        finalizeMs = started.elapsedNow().inWholeMilliseconds
                                        tokens.size
                                    }
                                    CommitRule.WholeWords -> StreamingText.wholeWordsEnd(tokens, committed)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                fail(error)
                                tokens.size
                            }
                        }
                        engineTime += started.elapsedNow()
                        val text = StreamingText.text(tokens, committed, end)
                        committed = end.coerceAtLeast(committed)
                        listener.onCommit(
                            command.windowIndex,
                            LocalCommit(
                                text = text,
                                rule = command.rule,
                                audioMs = samples * 1_000L / SAMPLE_RATE,
                                engineMs = engineTime.inWholeMilliseconds,
                                finalizeMs = finalizeMs,
                                waitMs = waitMs,
                                partials = partials,
                                afterFailure = afterFailure
                            )
                        )
                        samples = 0L
                        engineTime = Duration.ZERO
                        partials = 0
                        val rest = StreamingText.text(tokens, committed)
                        if (rest != shown) {
                            shown = rest
                            listener.onPartial(rest)
                        }
                        if (command.last) break
                    }
                }
            }
        } finally {
            // Na thread do motor, mesmo cancelado: é o que solta o modelo para a liberação por ócio.
            withContext(NonCancellable) {
                try {
                    engine.close()
                } catch (_: Throwable) {
                }
                listener.onClosed(stats.snapshot())
            }
        }
    }

    private class StatsCounter(private val timeSource: TimeSource) {
        private var count = 0
        private var last: TimeMark? = null
        private var gapTotalMs = 0L
        private var gaps = 0
        private var maxGapMs = 0L

        fun partial() {
            count++
            val now = timeSource.markNow()
            last?.let { previous ->
                val gap = (previous.elapsedNow() - now.elapsedNow()).inWholeMilliseconds
                gapTotalMs += gap
                gaps++
                if (gap > maxGapMs) maxGapMs = gap
            }
            last = now
        }

        fun snapshot() = LocalSessionStats(
            partials = count,
            meanPartialGapMs = if (gaps == 0) null else gapTotalMs / gaps,
            maxPartialGapMs = if (gaps == 0) null else maxGapMs
        )
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}

data class LocalSessionStats(val partials: Int, val meanPartialGapMs: Long?, val maxPartialGapMs: Long?)
