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
    private val openLine: (JavaSoundFormat) -> TargetDataLine = ::defaultTargetDataLine
) : AudioCaptureEngine {
    private val captureActive = AtomicBoolean(false)

    @Volatile
    private var line: TargetDataLine? = null
    private var captureThread: Thread? = null

    init {
        require(format.sampleBits == 16) { "sampleBits must be 16" }
    }

    override val isRunning: Boolean
        get() = captureActive.get() && (line?.isActive ?: false)

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
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
        val thread = Thread({ captureLoop(target, onFrame) }, THREAD_NAME).apply { isDaemon = true }
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

    private fun captureLoop(target: TargetDataLine, onFrame: suspend (AudioFrame) -> Unit) {
        val buffer = ByteArray(readBufferBytes())
        try {
            while (captureActive.get()) {
                val bytesRead = target.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    runBlocking { onFrame(AudioFrame(buffer.copyOf(bytesRead), format)) }
                } else if (!target.isOpen) {
                    captureActive.set(false)
                }
            }
        } catch (error: Throwable) {
            captureActive.set(false)
            System.err.println("$TAG: audio capture failed: ${error::class.simpleName}: ${error.message}")
        } finally {
            runCatching { target.stop() }
            runCatching { target.close() }
        }
    }

    private fun readBufferBytes(): Int =
        (format.sampleRate * format.bytesPerFrame / READS_PER_SECOND).coerceAtLeast(format.bytesPerFrame)

    private fun wrapStartFailure(error: Throwable): AudioCaptureException = when (error) {
        is AudioCaptureException -> error
        is LineUnavailableException -> AudioCaptureException("microphone line unavailable", error)
        is SecurityException -> AudioCaptureException("microphone access denied", error)
        else -> AudioCaptureException("failed to start audio capture", error)
    }

    private companion object {
        private const val TAG = "FlowVoiceDictation"
        private const val THREAD_NAME = "flowvoice-audio-capture"
        private const val READS_PER_SECOND = 10
        private const val LINE_BUFFER_MULTIPLIER = 4
        private const val STOP_JOIN_TIMEOUT_MS = 1_000L
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
