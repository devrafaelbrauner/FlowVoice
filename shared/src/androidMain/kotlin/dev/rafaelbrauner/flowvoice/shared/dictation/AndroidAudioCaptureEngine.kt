package dev.rafaelbrauner.flowvoice.shared.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat as PlatformAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

class AndroidAudioCaptureEngine(
    context: Context,
    override val format: AudioFormat = AudioFormat.DEFAULT
) : AudioCaptureEngine {
    private val appContext = context.applicationContext
    private val captureActive = AtomicBoolean(false)
    private var recordingHandle: RecordingHandle? = null
    private var captureThread: Thread? = null

    init {
        require(format.sampleBits == 16) { "sampleBits must be 16" }
    }

    override val isRunning: Boolean
        get() = captureActive.get() && (recordingHandle?.isRecording() ?: false)

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) = start(onFrame, onError = {})

    override suspend fun start(
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) {
        if (!captureActive.compareAndSet(false, true)) {
            throw AudioCaptureException("audio capture is already running")
        }
        try {
            startCapture(onFrame, onError)
        } catch (error: CancellationException) {
            stop()
            throw error
        } catch (error: AudioCaptureException) {
            stop()
            throw error
        } catch (error: Throwable) {
            stop()
            throw AudioCaptureException("failed to start audio capture", error)
        }
    }

    private suspend fun startCapture(
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) {
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw AudioCaptureException("RECORD_AUDIO permission is not granted")
        }
        val channelMask = resolveChannelMask(format.channels)
        val minBufferSize = AudioRecord.getMinBufferSize(
            format.sampleRate,
            channelMask,
            PlatformAudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            throw AudioCaptureException("unsupported audio capture format: $format")
        }

        val readBuffer = ByteArray(minBufferSize.coerceAtLeast(MINIMUM_READ_BUFFER_BYTES) * 2)
        @Suppress("DEPRECATION")
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                format.sampleRate,
                channelMask,
                PlatformAudioFormat.ENCODING_PCM_16BIT,
                readBuffer.size
            )
        } catch (error: Throwable) {
            throw AudioCaptureException("failed to create AudioRecord", error)
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { audioRecord.release() }
            throw AudioCaptureException("AudioRecord failed to initialize")
        }

        val handle = RecordingHandle(audioRecord)
        recordingHandle = handle
        val startSignal = CompletableDeferred<Unit>()
        val thread = Thread(
            {
                var failure: AudioCaptureException? = null
                try {
                    audioRecord.startRecording()
                    startSignal.complete(Unit)
                    Log.i(
                        TAG,
                        "audio capture started: sampleRate=${format.sampleRate}, channels=${format.channels}, sampleBits=${format.sampleBits}, readBufferBytes=${readBuffer.size}"
                    )
                    while (captureActive.get() && !Thread.currentThread().isInterrupted) {
                        val bytesRead = audioRecord.read(readBuffer, 0, readBuffer.size)
                        if (bytesRead > 0) {
                            runBlocking { onFrame(AudioFrame(readBuffer.copyOf(bytesRead), format)) }
                        } else {
                            Log.e(TAG, "audio capture read failed: code=$bytesRead, format=$format")
                            if (captureActive.getAndSet(false)) {
                                failure = AudioCaptureException("audio capture read failed: code=$bytesRead")
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    Log.w(TAG, "audio capture cancelled")
                    captureActive.set(false)
                    if (!startSignal.isCompleted) {
                        startSignal.completeExceptionally(error)
                    }
                } catch (error: Throwable) {
                    Log.e(TAG, "audio capture failed", error)
                    val wasActive = captureActive.getAndSet(false)
                    if (!startSignal.isCompleted) {
                        startSignal.completeExceptionally(error)
                    } else if (wasActive) {
                        failure = AudioCaptureException("audio capture failed", error)
                    }
                } finally {
                    handle.release()
                }
                failure?.let { runBlocking { onError(it) } }
            },
            THREAD_NAME
        )
        captureThread = thread
        thread.start()

        try {
            withTimeout(START_TIMEOUT_MS) { startSignal.await() }
        } catch (error: TimeoutCancellationException) {
            throw AudioCaptureException("timed out waiting for audio capture to start", error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw if (error is AudioCaptureException) error else AudioCaptureException("failed to start audio capture", error)
        }
    }

    override fun stop() {
        val wasActive = captureActive.getAndSet(false)
        recordingHandle?.stop()
        captureThread?.let { thread ->
            if (thread !== Thread.currentThread()) {
                runCatching { thread.join(STOP_JOIN_TIMEOUT_MS) }
                    .onFailure { Log.w(TAG, "timed out waiting for audio capture thread", it) }
            }
        }
        recordingHandle?.release()
        recordingHandle = null
        captureThread = null
        if (wasActive) {
            Log.i(TAG, "audio capture stopped")
        }
    }

    private fun resolveChannelMask(channels: Int): Int = when (channels) {
        1 -> PlatformAudioFormat.CHANNEL_IN_MONO
        2 -> PlatformAudioFormat.CHANNEL_IN_STEREO
        else -> throw AudioCaptureException("unsupported channel count: $channels")
    }

    private class RecordingHandle(private val audioRecord: AudioRecord) {
        private val released = AtomicBoolean(false)

        fun isRecording(): Boolean =
            runCatching {
                audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING
            }.getOrDefault(false)

        fun stop() {
            if (isRecording()) {
                runCatching { audioRecord.stop() }
                    .onFailure { Log.w(TAG, "failed to stop AudioRecord", it) }
            }
        }

        fun release() {
            if (released.compareAndSet(false, true)) {
                stop()
                runCatching { audioRecord.release() }
                    .onFailure { Log.w(TAG, "failed to release AudioRecord", it) }
            }
        }
    }

    private companion object {
        private const val TAG = "FlowVoiceDictation"
        private const val THREAD_NAME = "flowvoice-audio-capture"
        private const val MINIMUM_READ_BUFFER_BYTES = 4_096
        private const val START_TIMEOUT_MS = 3_000L
        private const val STOP_JOIN_TIMEOUT_MS = 1_000L
    }
}