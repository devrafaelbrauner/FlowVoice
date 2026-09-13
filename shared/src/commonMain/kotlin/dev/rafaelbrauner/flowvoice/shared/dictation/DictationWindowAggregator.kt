package dev.rafaelbrauner.flowvoice.shared.dictation

class DictationWindowAggregator(
    val targetDurationMs: Long = DEFAULT_TARGET_DURATION_MS,
    val format: AudioFormat = AudioFormat.DEFAULT
) {
    init {
        require(targetDurationMs > 0) { "targetDurationMs must be positive" }
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

        return if (bufferedDurationMs >= targetDurationMs) {
            listOf(buildWindow())
        } else {
            emptyList()
        }
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
        val pcm = ByteArray(bufferedBytes)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(pcm, offset)
            offset += chunk.size
        }

        val window = DictationWindow(
            index = nextWindowIndex,
            pcm = pcm,
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

    companion object {
        const val DEFAULT_TARGET_DURATION_MS = 4_000L
    }
}