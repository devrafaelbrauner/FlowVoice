package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DictationWindowAggregator(
    val targetDurationMs: Long = DEFAULT_TARGET_DURATION_MS,
    val format: AudioFormat = AudioFormat.DEFAULT,
    val pauseSearchBeforeMs: Long = 0L,
    val pauseSearchAfterMs: Long = 0L,
    val endpointing: SpeechEndpointing? = null
) {
    init {
        require(targetDurationMs > 0) { "targetDurationMs must be positive" }
        require(pauseSearchBeforeMs in 0 until targetDurationMs) { "pauseSearchBeforeMs must be in [0, targetDurationMs)" }
        require(pauseSearchAfterMs >= 0) { "pauseSearchAfterMs must not be negative" }
        if (endpointing != null) {
            require(format.bytesPerSample == 2) { "endpointing requires 16-bit PCM" }
            require(targetDurationMs > endpointing.minBufferedMs) { "targetDurationMs must exceed endpointing.minBufferedMs" }
        }
    }

    private val searchesPause = pauseSearchBeforeMs > 0 || pauseSearchAfterMs > 0
    private var buffer = ByteArray(0)
    private var bufferedBytes = 0
    private var consumedBytes = 0L
    private var nextWindowIndex = 0
    private val endpointer = endpointing?.let(::SpeechEndpointer)
    private val levelBlockBytes = bytesFor(SpeechEndpointing.BLOCK_MS)
    private val blockLevels = mutableListOf<Int>()

    // A linha do tempo vem dos bytes, não da soma de durações arredondadas dos frames: frames sem
    // milissegundos inteiros não fazem o corte nem o fim da sessão derivarem (P131).
    private val bufferedDurationMs: Long
        get() = format.durationMs(bufferedBytes)

    fun onFrame(frame: AudioFrame): List<DictationWindow> {
        require(frame.format == format) { "frame format must match aggregator format" }
        if (frame.pcm.isEmpty()) return emptyList()

        append(frame.pcm)

        endpointer?.let { return endpointWindows(it) }
        if (!searchesPause) {
            if (bufferedDurationMs < targetDurationMs) return emptyList()
            return listOf(emit(bufferedBytes, WindowCut.Target))
        }
        val windows = mutableListOf<DictationWindow>()
        while (bufferedDurationMs >= targetDurationMs + pauseSearchAfterMs) {
            windows += emit(pauseCut(), WindowCut.Target)
        }
        return windows
    }

    fun flush(): DictationWindow? {
        if (bufferedBytes == 0) return null
        return emit(bufferedBytes, WindowCut.Flush)
    }

    fun clear() {
        bufferedBytes = 0
        consumedBytes = 0L
        nextWindowIndex = 0
        blockLevels.clear()
        endpointer?.clear()
    }

    private fun append(pcm: ByteArray) {
        val needed = bufferedBytes + pcm.size
        if (buffer.size < needed) buffer = buffer.copyOf(max(needed, buffer.size * 2))
        pcm.copyInto(buffer, destinationOffset = bufferedBytes)
        bufferedBytes = needed
    }

    // Janela assim que há uma pausa real depois de fala (P140); sem pausa, o teto de alvo + depois
    // continua valendo, com o corte no trecho mais silencioso.
    private fun endpointWindows(endpointer: SpeechEndpointer): List<DictationWindow> {
        while ((blockLevels.size + 1) * levelBlockBytes <= bufferedBytes) {
            val level = blockLevel(blockLevels.size * levelBlockBytes)
            blockLevels += level
            endpointer.record(level)
        }
        val windows = mutableListOf<DictationWindow>()
        while (true) {
            val ceilingSearch = if (bufferedDurationMs >= targetDurationMs + pauseSearchAfterMs) {
                blocksAt(targetDurationMs - pauseSearchBeforeMs) until
                    min(blockLevels.size, blocksAt(targetDurationMs + pauseSearchAfterMs))
            } else {
                null
            }
            val next = endpointer.next(blockLevels, bufferedDurationMs, ceilingSearch) ?: break
            windows += emit(next.block.coerceAtLeast(1) * levelBlockBytes, next.cut, endpointer.noiseFloor)
        }
        return windows
    }

    private fun emit(cut: Int, reason: WindowCut, noiseFloor: Int? = null): DictationWindow {
        val window = DictationWindow(
            index = nextWindowIndex,
            pcm = buffer.copyOfRange(0, cut),
            format = format,
            startedAtMs = msAt(consumedBytes),
            finishedAtMs = msAt(consumedBytes + cut),
            cut = reason,
            noiseFloor = noiseFloor
        )

        nextWindowIndex++
        consumedBytes += cut
        buffer.copyInto(buffer, destinationOffset = 0, startIndex = cut, endIndex = bufferedBytes)
        bufferedBytes -= cut
        dropLevels(cut)

        return window
    }

    // Os cortes do modo adaptativo caem em fronteira de bloco, então os níveis que sobram continuam
    // valendo; o flush esvazia o buffer.
    private fun dropLevels(cut: Int) {
        if (endpointer == null) return
        if (cut % levelBlockBytes == 0 && cut / levelBlockBytes <= blockLevels.size) {
            blockLevels.subList(0, cut / levelBlockBytes).clear()
        } else {
            blockLevels.clear()
            while ((blockLevels.size + 1) * levelBlockBytes <= bufferedBytes) {
                blockLevels += blockLevel(blockLevels.size * levelBlockBytes)
            }
        }
    }

    // Cortar exatamente no alvo parte palavras ao meio, e cada janela é transcrita sozinha (P131).
    // O corte vai para o meio do trecho de 120 ms com menor energia média entre alvo − antes e
    // alvo + depois: uma oclusiva (p, t, k) dura 30–100 ms e não ganha de uma pausa entre palavras.
    private fun pauseCut(): Int {
        val atTarget = min(bufferedBytes, bytesFor(targetDurationMs))
        if (format.bytesPerSample != 2) return atTarget
        val blockBytes = bytesFor(PAUSE_BLOCK_MS)
        val start = bytesFor(targetDurationMs - pauseSearchBeforeMs)
        val end = min(bufferedBytes, bytesFor(targetDurationMs + pauseSearchAfterMs))
        val energies = (start until end step blockBytes)
            .takeWhile { it + blockBytes <= end }
            .map { energy(it, blockBytes) }
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

    private fun blockLevel(offset: Int): Int =
        (energy(offset, levelBlockBytes) / (levelBlockBytes / format.bytesPerSample)).toInt()

    private fun energy(offset: Int, length: Int): Long {
        var sum = 0L
        var index = offset
        while (index + 1 < offset + length) {
            val low = buffer[index].toInt() and 0xFF
            val high = buffer[index + 1].toInt() shl 8
            sum += abs((high or low).toShort().toInt())
            index += 2
        }
        return sum
    }

    private fun blocksAt(durationMs: Long): Int = (durationMs / SpeechEndpointing.BLOCK_MS).toInt()

    private fun bytesFor(durationMs: Long): Int =
        (durationMs * format.sampleRate / 1_000L).toInt() * format.bytesPerFrame

    private fun msAt(bytes: Long): Long = bytes * 1_000L / (format.bytesPerFrame * format.sampleRate.toLong())

    companion object {
        const val DEFAULT_TARGET_DURATION_MS = 4_000L
        const val SPEECH_PAUSE_SEARCH_BEFORE_MS = 900L
        const val SPEECH_PAUSE_SEARCH_AFTER_MS = 300L
        private const val PAUSE_BLOCK_MS = 20L
        private const val PAUSE_RUN_BLOCKS = 6
    }
}
