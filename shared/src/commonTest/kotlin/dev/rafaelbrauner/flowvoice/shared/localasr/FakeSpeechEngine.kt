package dev.rafaelbrauner.flowvoice.shared.localasr

import kotlinx.coroutines.CoroutineDispatcher

// Motor de mentira: o roteiro diz que tokens o motor emite ao receber o n-ésimo bloco de áudio
// (a contar de 1). `held` são tokens que o modelo só solta com a cauda de silêncio
// (a última palavra presa no bloco que não fechou).
internal class FakeSpeechEngine(
    private val emitted: Map<Int, List<String>> = emptyMap(),
    private val heldUntilFinalize: Map<Int, List<String>> = emptyMap(),
    private val failOnChunk: Int? = null,
    private val failOnStart: Boolean = false,
    private val failOnFinalize: Boolean = false
) : StreamingSpeechEngine {
    override val name: String = "fake"

    val calls = mutableListOf<String>()
    private val tokens = mutableListOf<String>()
    private val held = mutableListOf<String>()
    var closed = 0
        private set

    override fun start(language: String) {
        calls += "start:$language"
        if (failOnStart) error("modelo não carregou")
    }

    private var accepted = 0

    override fun accept(samples: FloatArray) {
        val chunk = ++accepted
        calls += "accept:$chunk"
        if (chunk == failOnChunk) error("falha nativa")
        tokens += emitted[chunk].orEmpty()
        held += heldUntilFinalize[chunk].orEmpty()
    }

    override fun tokens(): List<String> = tokens.toList()

    override fun finishUtterance() {
        calls += "finish"
        if (failOnFinalize) error("falha nativa no fim")
        tokens += held
        held.clear()
    }

    override fun close() {
        calls += "close"
        closed++
    }
}

internal class FakeSpeechEngines(
    override val dispatcher: CoroutineDispatcher,
    var installed: Boolean = true,
    private val factory: () -> StreamingSpeechEngine
) : LocalSpeechEngines {
    val created = mutableListOf<StreamingSpeechEngine>()

    override fun modelInstalled(): Boolean = installed

    override fun create(): StreamingSpeechEngine = factory().also { created += it }
}

// Um bloco de áudio qualquer; `id` só documenta a ordem no teste (o motor conta os blocos).
@Suppress("UNUSED_PARAMETER")
internal fun chunk(id: Int): FloatArray = FloatArray(160)
