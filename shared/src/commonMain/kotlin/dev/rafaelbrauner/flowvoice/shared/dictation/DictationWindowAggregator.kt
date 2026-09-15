package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.math.abs
import kotlin.math.min

class DictationWindowAggregator(
    val targetDurationMs: Long = DEFAULT_TARGET_DURATION_MS,
    val format: AudioFormat = AudioFormat.DEFAULT,
    val pauseSearchMs: Long = 0L
) {
    init {
        require(targetDurationMs > 0) { "targetDurationMs must be positive" }
        require(pauseSearchMs in 0 until targetDurationMs) { "pauseSearchMs must be in [0, targetDurationMs)" }
    }

    private val chunks = mutableListOf<ByteArray>()
    private var bufferedBytes = 0
    private var bufferedDurationMs = 0L
    private var nextWindowIndex = 0
    private var nextWindowStartMs = 0L

    fun onFrame(frame: AudioFrame): List<DictationWindow> {
        require(frame.format == format) { "frame format must match aggregator format" }
        if (frame.pcm.isEmpty()) return emptyList()

        chunks.add(frame.pcm)
        bufferedBytes += frame.pcm.size
        bufferedDurationMs += frame.durationMs

        if (pauseSearchMs == 0L) {
            return if (bufferedDurationMs >= targetDurationMs) listOf(buildWindow()) else emptyList()
        }
        val windows = mutableListOf<DictationWindow>()
        while (bufferedDurationMs >= targetDurationMs + pauseSearchMs) {
            windows += cutAtPause()
        }
        return windows
    }

    fun flush(): DictationWindow? {
        if (bufferedBytes == 0) return null
        return buildWindow()
    }

    fun clear() {
        chunks.clear()
        bufferedBytes = 0
        bufferedDurationMs = 0
        nextWindowIndex = 0
        nextWindowStartMs = 0
    }

    private fun buildWindow(): DictationWindow {
        val window = DictationWindow(
            index = nextWindowIndex,
            pcm = bufferedPcm(),
            format = format,
            startedAtMs = nextWindowStartMs,
            finishedAtMs = nextWindowStartMs + bufferedDurationMs
        )

        nextWindowIndex++
        nextWindowStartMs = window.finishedAtMs
        chunks.clear()
        bufferedBytes = 0
        bufferedDurationMs = 0

        return window
    }

    // Cortar exatamente no alvo parte palavras ao meio, e cada janela é transcrita sozinha (P131):
    // o corte vai para o meio do trecho de 20 ms mais silencioso perto do alvo, e o resto do áudio
    // abre a janela seguinte.
    private fun cutAtPause(): DictationWindow {
        val pcm = bufferedPcm()
        val cut = quietestCut(pcm)
        val durationMs = format.durationMs(cut)
        val window = DictationWindow(
            index = nextWindowIndex,
            pcm = pcm.copyOfRange(0, cut),
            format = format,
            startedAtMs = nextWindowStartMs,
            finishedAtMs = nextWindowStartMs + durationMs
        )

        nextWindowIndex++
        nextWindowStartMs = window.finishedAtMs
        chunks.clear()
        val remainder = pcm.copyOfRange(cut, pcm.size)
        if (remainder.isNotEmpty()) chunks.add(remainder)
        bufferedBytes = remainder.size
        bufferedDurationMs -= durationMs

        return window
    }

    private fun quietestCut(pcm: ByteArray): Int {
        val atTarget = min(pcm.size, bytesFor(targetDurationMs))
        if (format.bytesPerSample != 2) return atTarget
        val blockBytes = bytesFor(PAUSE_BLOCK_MS)
        val end = min(pcm.size, bytesFor(targetDurationMs + pauseSearchMs))
        var offset = bytesFor(targetDurationMs - pauseSearchMs)
        var best = -1
        var bestEnergy = Long.MAX_VALUE
        while (offset + blockBytes <= end) {
            val energy = energy(pcm, offset, blockBytes)
            if (energy < bestEnergy) {
                bestEnergy = energy
                best = offset
            }
            offset += blockBytes
        }
        return if (best < 0) atTarget else best + bytesFor(PAUSE_BLOCK_MS / 2)
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
        const val SPEECH_PAUSE_SEARCH_MS = 600L
        private const val PAUSE_BLOCK_MS = 20L
    }
}
