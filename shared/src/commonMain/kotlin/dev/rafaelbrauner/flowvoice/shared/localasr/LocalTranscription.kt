package dev.rafaelbrauner.flowvoice.shared.localasr

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFrame
import dev.rafaelbrauner.flowvoice.shared.dictation.CaptureTap
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.WindowCut
import dev.rafaelbrauner.flowvoice.shared.transcription.SessionTranscription
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionEventLog
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import dev.rafaelbrauner.flowvoice.shared.transcription.locked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlin.time.TimeSource

// O motor no aparelho do ponto de vista do pipeline: ouve a captura na ordem exata (`CaptureTap`) e
// entrega, por janela, o texto do fluxo contínuo desde o corte anterior — como `TranscriptionSegment`
// com `continuous = true`, para seguir o mesmo caminho das janelas da nuvem.
//
// Onde fecha cada pedaço: a janela cortada numa pausa (e a última, no fim do ditado) fecha com o
// silêncio de cauda e leva tudo o que foi dito; a cortada no teto, sem pausa, leva só as palavras
// inteiras (`StreamingText.wholeWordsEnd`). Nenhum corte parte palavra.
//
// Sem teto de requisições nem trava de áudio vazio: nada disso custa aqui. O teto de duração fica no
// pipeline.
class LocalTranscription(
    private val engines: LocalSpeechEngines,
    private val scope: CoroutineScope,
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp,
    private val timeSource: TimeSource = TimeSource.Monotonic
) : SessionTranscription, CaptureTap {
    private val lock = Any()
    private var generation = 0
    private var driver: LocalDictationDriver? = null
    private var lastWindowIndex = -1
    private var ended = false

    private val segmentState = MutableStateFlow<List<TranscriptionSegment>>(emptyList())
    private val partialState = MutableStateFlow("")
    private val failureState = MutableStateFlow<Throwable?>(null)
    private val pendingCuts = MutableStateFlow(0)

    override val segments: StateFlow<List<TranscriptionSegment>> = segmentState.asStateFlow()

    // Texto reconhecido desde o último pedaço fechado. Provisório: só prévia, nunca digitado.
    val partial: StateFlow<String> = partialState.asStateFlow()

    // Primeira falha do motor na sessão (null = sem falha).
    val failure: StateFlow<Throwable?> = failureState.asStateFlow()

    // Abre o fluxo do ditado. A carga do modelo corre na thread do motor; o áudio espera na fila.
    fun begin(language: String) {
        val session = synchronizedState {
            generation++
            driver?.cancel()
            driver = null
            lastWindowIndex = -1
            ended = false
            segmentState.value = emptyList()
            partialState.value = ""
            failureState.value = null
            pendingCuts.value = 0
            generation
        }
        val engine = engines.create()
        val created = LocalDictationDriver(
            engine = engine,
            language = language,
            scope = scope,
            engineDispatcher = engines.dispatcher,
            listener = Listener(session, engine.name),
            timeSource = timeSource
        )
        synchronizedState { if (session == generation) driver = created else created.cancel() }
    }

    override fun onFrame(frame: AudioFrame) {
        val current = synchronizedState { driver.takeUnless { ended } } ?: return
        current.audio(pcm16ToFloat(frame.pcm))
    }

    override fun onWindow(window: DictationWindow) {
        val current = synchronizedState {
            val active = driver.takeUnless { ended } ?: return
            lastWindowIndex = maxOf(lastWindowIndex, window.index)
            pendingCuts.update { it + 1 }
            upsert(TranscriptionSegment(window.index, TranscriptionSegment.Status.Transcribing, continuous = true))
            active
        }
        current.cut(window.index, ruleFor(window.cut))
    }

    // As janelas já chegam pelo `onWindow`, no instante do corte e na ordem do áudio.
    override fun submit(window: DictationWindow) = Unit

    // A captura acabou (fim do ditado ou parada antecipada): o que o motor ainda segura sai com a
    // cauda num último pedaço, depois da última janela, e o fluxo fecha.
    fun endOfAudio() {
        val (current, index) = synchronizedState {
            val active = driver.takeUnless { ended } ?: return
            ended = true
            pendingCuts.update { it + 1 }
            active to lastWindowIndex + 1
        }
        current.cut(index, CommitRule.Finalize, last = true)
    }

    override suspend fun awaitIdle() {
        pendingCuts.first { it == 0 }
    }

    override fun cancel() {
        val current = synchronizedState {
            generation++
            ended = true
            pendingCuts.value = 0
            partialState.value = ""
            driver.also { driver = null }
        }
        current?.cancel()
    }

    private inner class Listener(private val session: Int, private val engineName: String) : LocalDictationDriver.Listener {
        override fun onReady(loadMs: Long) {
            if (!isLive(session)) return
            eventLog.log("local_engine_ready", mapOf("engine" to engineName, "loadMs" to loadMs.toString()))
        }

        override fun onPartial(text: String) {
            synchronizedState { if (session == generation) partialState.value = text }
        }

        override fun onCommit(windowIndex: Int, commit: LocalCommit) {
            val live = synchronizedState {
                if (session != generation) return@synchronizedState false
                val registered = segmentState.value.any { it.windowIndex == windowIndex }
                // O último pedaço (depois da última janela) só vira segmento se tiver texto.
                if (registered || commit.text.isNotBlank()) {
                    upsert(
                        TranscriptionSegment(
                            windowIndex = windowIndex,
                            status = TranscriptionSegment.Status.Ok,
                            text = commit.text,
                            continuous = true
                        )
                    )
                }
                pendingCuts.update { (it - 1).coerceAtLeast(0) }
                true
            }
            if (!live) return
            eventLog.log(
                "local_commit",
                buildMap {
                    put("window", (windowIndex + 1).toString())
                    put("rule", if (commit.rule == CommitRule.Finalize) "finalize" else "whole_words")
                    put("chars", commit.text.length.toString())
                    put("audioMs", commit.audioMs.toString())
                    put("engineMs", commit.engineMs.toString())
                    put("finalizeMs", commit.finalizeMs.toString())
                    put("waitMs", commit.waitMs.toString())
                    put("partials", commit.partials.toString())
                    if (commit.afterFailure) put("afterFailure", "true")
                }
            )
        }

        override fun onFailure(error: Throwable) {
            val live = synchronizedState {
                if (session != generation) return@synchronizedState false
                failureState.value = error
                true
            }
            // Só o tipo do erro: a mensagem de uma exceção nativa pode trazer caminho de arquivo.
            if (live) eventLog.log("local_engine_failed", mapOf("engine" to engineName, "error" to (error::class.simpleName ?: "erro")))
        }

        override fun onClosed(stats: LocalSessionStats) {
            eventLog.log(
                "local_engine_closed",
                buildMap {
                    put("engine", engineName)
                    put("partials", stats.partials.toString())
                    stats.meanPartialGapMs?.let { put("meanPartialGapMs", it.toString()) }
                    stats.maxPartialGapMs?.let { put("maxPartialGapMs", it.toString()) }
                }
            )
        }
    }

    private fun isLive(session: Int): Boolean = synchronizedState { session == generation }

    private fun upsert(segment: TranscriptionSegment) {
        segmentState.update { current ->
            val index = current.indexOfFirst { it.windowIndex == segment.windowIndex }
            if (index >= 0) {
                current.toMutableList().also { it[index] = segment }
            } else {
                (current + segment).sortedBy { it.windowIndex }
            }
        }
    }

    private inline fun <T> synchronizedState(block: () -> T): T = locked(lock, block)

    companion object {
        fun ruleFor(cut: WindowCut): CommitRule = when (cut) {
            WindowCut.Pause, WindowCut.Flush -> CommitRule.Finalize
            WindowCut.Target, WindowCut.Leading, WindowCut.Ceiling -> CommitRule.WholeWords
        }

        // PCM de 16 bits little-endian para float em [-1, 1).
        fun pcm16ToFloat(pcm: ByteArray): FloatArray {
            val samples = FloatArray(pcm.size / 2)
            for (index in samples.indices) {
                val low = pcm[2 * index].toInt() and 0xFF
                val high = pcm[2 * index + 1].toInt() shl 8
                samples[index] = (high or low).toShort() / 32_768f
            }
            return samples
        }
    }
}
