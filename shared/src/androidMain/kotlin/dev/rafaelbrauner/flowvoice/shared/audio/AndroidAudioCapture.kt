package dev.rafaelbrauner.flowvoice.shared.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidAudioCapture(
    private val config: VadConfig = VadConfig(),
    private val onFrame: (FloatArray) -> Unit,
    private val scope: CoroutineScope
) {

    private var record: AudioRecord? = null
    private var job: Job? = null

    fun start(): Boolean {
        if (job?.isActive == true) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val bufferSize = maxOf(minBuffer, config.frameSamples * 4)
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                config.sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: SecurityException) {
            return false
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return false
        }
        record = audioRecord
        return try {
            audioRecord.startRecording()
            job = scope.launch(Dispatchers.IO) { readLoop(audioRecord) }
            true
        } catch (_: IllegalStateException) {
            audioRecord.release()
            record = null
            false
        }
    }

    suspend fun stop() {
        job?.cancel()
        job?.join()
        job = null
        withContext(Dispatchers.IO) {
            runCatching { record?.stop() }
            record?.release()
            record = null
        }
    }

    private suspend fun readLoop(audioRecord: AudioRecord) {
        val chunk = ShortArray(config.frameSamples)
        val scope = this
        while (scope.job?.isActive == true) {
            val read = withContext(Dispatchers.IO) {
                audioRecord.read(chunk, 0, chunk.size)
            }
            if (read <= 0) continue
            val floats = FloatArray(read) { i -> (chunk[i] / 32768f).coerceIn(-1f, 1f) }
            onFrame(floats)
        }
    }
}
