package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.math.abs
import kotlin.math.min

class DictationWindowAggregator(
    val targetDurationMs: Long = DEFAULT_TARGET_DURATION_MS,
    val format: AudioFormat = AudioFormat.DEFAULT,
    val pauseSearchBeforeMs: Long = 0L,
    val pauseSearchAfterMs: Long = 0L
) {
    init {
        require(targetDurationMs > 0) { "targetDurationMs must be positive" }
        require(pauseSearchBeforeMs in 0 until targetDurationMs) { "pauseSearchBeforeMs must be in [0, targetDurationMs)" }
        require(pauseSearchAfterMs >= 0) { "pauseSearchAfterMs must not be negative" }
    }

    private val searchesPause = pauseSearchBeforeMs > 0 || pauseSearchAfterMs > 0
    private val chunks = mutableListOf<ByteArray>()
    private var bufferedBytes = 0
    private var consumedBytes = 0L
    private var nextWindowIndex = 0

    // A linha do tempo vem dos bytes, não da soma de durações arredondadas dos frames: frames sem
    // milissegundos inteiros não fazem o corte nem o fim da sessão derivarem (P131).
    private val bufferedDurationMs: Long
        get() = format.durationMs(bufferedBytes)

    fun onFrame(frame: AudioFrame): List<DictationWindow> {
        require(frame.format == format) { "frame format must match aggregator format" }
        if (frame.pcm.isEmpty()) return emptyList()

        chunks.add(frame.pcm)
        bufferedBytes += frame.pcm.size

        if (!searchesPause) {
            if (bufferedDurationMs < targetDurationMs) return emptyList()
            val pcm = bufferedPcm()
            return listOf(emit(pcm, pcm.size))
        }
        val windows = mutableListOf<DictationWindow>()
        while (bufferedDurationMs >= targetDurationMs + pauseSearchAfterMs) {
            val pcm = bufferedPcm()
            windows += emit(pcm, pauseCut(pcm))
        }
        return windows
    }

    fun flush(): DictationWindow? {
        if (bufferedBytes == 0) return null
        val pcm = bufferedPcm()
        return emit(pcm, pcm.size)
    }

    fun clear() {
        chunks.clear()
        bufferedBytes = 0
        consumedBytes = 0L
        nextWindowIndex = 0
    }

    private fun emit(pcm: ByteArray, cut: Int): DictationWindow {
        val window = DictationWindow(
            index = nextWindowIndex,
            pcm = if (cut == pcm.size) pcm else pcm.copyOfRange(0, cut),
            format = format,
            startedAtMs = msAt(consumedBytes),
            finishedAtMs = msAt(consumedBytes + cut)
        )

        nextWindowIndex++
        consumedBytes += cut
        chunks.clear()
        bufferedBytes = pcm.size - cut
        if (bufferedBytes > 0) chunks.add(pcm.copyOfRange(cut, pcm.size))

        return window
    }

    // Cortar exatamente no alvo parte palavras ao meio, e cada janela é transcrita sozinha (P131).
    // O corte vai para o meio do trecho de 120 ms com menor energia média entre alvo − antes e
    // alvo + depois: uma oclusiva (p, t, k) dura 30–100 ms e não ganha de uma pausa entre palavras.
    private fun pauseCut(pcm: ByteArray): Int {
        val atTarget = min(pcm.size, bytesFor(targetDurationMs))
        if (format.bytesPerSample != 2) return atTarget
        val blockBytes = bytesFor(PAUSE_BLOCK_MS)
        val start = bytesFor(targetDurationMs - pauseSearchBeforeMs)
        val end = min(pcm.size, bytesFor(targetDurationMs + pauseSearchAfterMs))
        val energies = (start until end step blockBytes)
            .takeWhile { it + blockBytes <= end }
            .map { energy(pcm, it, blockBytes) }
        if (energies.isEmpty()) return atTarget
        val run = min(PAUSE_RUN_BLOCKS, energies.size)
        var runEnergy = energies.take(run).sum()
        var bestEnergy = runEnergy
        var bestBlock = 0
        for (block in 1..energies.size - run) {
            runEnergy += energies[block + run - 1] - energies[block - 1]
            if (runEnergy < bestEnergy) {
                bestEnergy = runEnergy
                bestBlock = block
            }
        }
        return start + bestBlock * blockBytes + bytesFor(PAUSE_BLOCK_MS * run / 2)
    }

    private fun energy(pcm: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var index = offset
        while (index + 1 < offset + length) {
            val low = pcm[index].toInt() and 0xFF
            val high = pcm[index + 1].toInt() shl 8
            sum += abs((high or low).toShort().toInt())
            index += 2
        }
        return sum
    }

    private fun bytesFor(durationMs: Long): Int =
        (durationMs * format.sampleRate / 1_000L).toInt() * format.bytesPerFrame

    private fun msAt(bytes: Long): Long = bytes * 1_000L / (format.bytesPerFrame * format.sampleRate.toLong())

    private fun bufferedPcm(): ByteArray {
        val pcm = ByteArray(bufferedBytes)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(pcm, offset)
            offset += chunk.size
        }
        return pcm
    }

    companion object {
        const val DEFAULT_TARGET_DURATION_MS = 4_000L
        const val SPEECH_PAUSE_SEARCH_BEFORE_MS = 900L
        const val SPEECH_PAUSE_SEARCH_AFTER_MS = 300L
        private const val PAUSE_BLOCK_MS = 20L
        private const val PAUSE_RUN_BLOCKS = 6
    }
}
