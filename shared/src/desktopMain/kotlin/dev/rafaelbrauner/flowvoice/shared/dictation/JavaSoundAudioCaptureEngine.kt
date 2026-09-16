package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioFormat as JavaSoundFormat

class JavaSoundAudioCaptureEngine(
    override val format: AudioFormat = AudioFormat.DEFAULT,
    private val openLine: (JavaSoundFormat) -> TargetDataLine = ::defaultTargetDataLine,
    private val stalledReadTimeoutMs: Long = DEFAULT_STALLED_READ_TIMEOUT_MS
) : AudioCaptureEngine {
    private val captureActive = AtomicBoolean(false)

    @Volatile
    private var line: TargetDataLine? = null
    private var captureThread: Thread? = null

    init {
        require(format.sampleBits == 16) { "sampleBits must be 16" }
        require(stalledReadTimeoutMs > 0L) { "stalledReadTimeoutMs must be positive" }
    }

    override val isRunning: Boolean
        get() = captureActive.get() && (line?.isActive ?: false)

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) = start(onFrame, onError = {})

    override suspend fun start(
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) {
        if (!captureActive.compareAndSet(false, true)) {
            throw AudioCaptureException("audio capture is already running")
        }
        val javaFormat = format.toJavaSoundFormat()
        val target = try {
            openLine(javaFormat).also { opened ->
                try {
                    opened.open(javaFormat, readBufferBytes() * LINE_BUFFER_MULTIPLIER)
                    opened.start()
                } catch (error: Throwable) {
                    runCatching { opened.close() }
                    throw error
                }
            }
        } catch (error: Throwable) {
            captureActive.set(false)
            throw wrapStartFailure(error)
        }
        line = target
        val thread = Thread({ captureLoop(target, onFrame, onError) }, THREAD_NAME).apply { isDaemon = true }
        captureThread = thread
        thread.start()
    }

    override fun stop() {
        captureActive.set(false)
        line?.let { runCatching { it.stop() } }
        captureThread?.let { thread ->
            if (thread !== Thread.currentThread()) {
                runCatching { thread.join(STOP_JOIN_TIMEOUT_MS) }
            }
        }
        line?.let { runCatching { it.close() } }
        line = null
        captureThread = null
    }

    private fun captureLoop(
        target: TargetDataLine,
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) {
        val buffer = ByteArray(readBufferBytes())
        var failure: AudioCaptureException? = null
        var silentSinceNanos: Long? = null
        try {
            while (captureActive.get()) {
                val bytesRead = target.read(buffer, 0, buffer.size)
                when {
                    bytesRead > 0 -> {
                        silentSinceNanos = null
                        runBlocking { onFrame(AudioFrame(buffer.copyOf(bytesRead), format)) }
                    }
                    !target.isOpen -> {
                        if (captureActive.getAndSet(false)) {
                            failure = AudioCaptureException("microphone line closed unexpectedly")
                        }
                    }
                    else -> {
                        val now = System.nanoTime()
                        val silentSince = silentSinceNanos ?: now.also { silentSinceNanos = it }
                        if (now - silentSince >= stalledReadTimeoutMs * NANOS_PER_MILLI) {
                            if (captureActive.getAndSet(false)) {
                                failure = AudioCaptureException("microphone delivered no audio for $stalledReadTimeoutMs ms")
                            }
                        } else {
                            Thread.sleep(STALLED_READ_PAUSE_MS)
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            if (captureActive.getAndSet(false)) {
                failure = AudioCaptureException("audio capture failed", error)
            }
            System.err.println("$TAG: audio capture failed: ${error::class.simpleName}: ${error.message}")
        } finally {
            runCatching { target.stop() }
            runCatching { target.close() }
        }
        failure?.let { runBlocking { onError(it) } }
    }

    private fun readBufferBytes(): Int = CaptureFrames.readBytes(format)

    private fun wrapStartFailure(error: Throwable): AudioCaptureException = when (error) {
        is AudioCaptureException -> error
        is LineUnavailableException -> AudioCaptureException("microphone line unavailable", error)
        is SecurityException -> AudioCaptureException("microphone access denied", error)
        else -> AudioCaptureException("failed to start audio capture", error)
    }

    companion object {
        const val DEFAULT_STALLED_READ_TIMEOUT_MS = 2_000L
        private const val TAG = "FlowVoiceDictation"
        private const val THREAD_NAME = "flowvoice-audio-capture"
        private const val LINE_BUFFER_MULTIPLIER = 4
        private const val STOP_JOIN_TIMEOUT_MS = 1_000L
        private const val STALLED_READ_PAUSE_MS = 20L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

internal fun AudioFormat.toJavaSoundFormat(): JavaSoundFormat =
    JavaSoundFormat(sampleRate.toFloat(), sampleBits, channels, true, false)

private fun defaultTargetDataLine(format: JavaSoundFormat): TargetDataLine {
    val info = DataLine.Info(TargetDataLine::class.java, format)
    if (!AudioSystem.isLineSupported(info)) {
        throw AudioCaptureException("no microphone line supports $format")
    }
    return AudioSystem.getLine(info) as TargetDataLine
}
