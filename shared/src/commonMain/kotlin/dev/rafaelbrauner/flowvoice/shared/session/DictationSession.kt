package dev.rafaelbrauner.flowvoice.shared.session

import dev.rafaelbrauner.flowvoice.shared.audio.VoiceActivityDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class DictationSession(
    private val router: StreamRouter,
    private val vad: VoiceActivityDetector,
    private val config: DictationConfig = DictationConfig(),
    private val scope: CoroutineScope
) {

    private val workerId = AtomicLong(0)
    private var workerJob: Job? = null
    private var workerChannel: Channel<StreamCmd>? = null

    private val mutableState = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutableLiveText = MutableStateFlow(StreamText("", ""))
    val liveText: StateFlow<StreamText> = mutableLiveText.asStateFlow()

    private val sessionScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    fun start() {
        if (router.isOpen) return
        vad.reset()
        val channel = router.open()
        workerChannel = channel
        val id = workerId.incrementAndGet()
        mutableState.value = SessionState.LISTENING
        mutableLiveText.value = StreamText("", "")
        workerJob = sessionScope.launch {
            runWorker(id, channel)
        }
    }

    suspend fun stop(): FinalizedStreamText? {
        val channel = router.take() ?: return null
        val reply = kotlinx.coroutines.CompletableDeferred<FinalizedStreamText?>()
        channel.send(StreamCmd.Finalize(reply))
        val result = reply.await()
        workerJob?.join()
        workerJob = null
        workerChannel = null
        mutableState.value = SessionState.IDLE
        return result
    }

    suspend fun cancel() {
        val channel = router.take()
        channel?.send(StreamCmd.Cancel)
        workerJob?.cancelAndJoin()
        workerJob = null
        workerChannel = null
        mutableState.value = SessionState.IDLE
        mutableLiveText.value = StreamText("", "")
    }

    private suspend fun runWorker(id: Long, channel: Channel<StreamCmd>) {
        val pcm = mutableListOf<Float>()
        for (cmd in channel) {
            if (id != workerId.get()) break
            when (cmd) {
                is StreamCmd.Feed -> {
                    pcm.addAll(cmd.pcm.asList())
                    mutableState.value = SessionState.LISTENING
                }
                is StreamCmd.Finalize -> {
                    mutableState.value = SessionState.WORKING
                    val finalized = if (pcm.isEmpty()) {
                        null
                    } else {
                        FinalizedStreamText(
                            text = "",
                            audio = pcm.toFloatArray(),
                            sampleRateHz = config.sampleRateHz
                        )
                    }
                    cmd.reply.complete(finalized)
                    break
                }
                StreamCmd.Cancel -> break
                is StreamCmd.Snapshot -> {
                    cmd.reply.complete(pcm.toFloatArray())
                }
            }
        }
    }
}

data class DictationConfig(
    val sampleRateHz: Int = 16000,
    val windowMs: Long = 2500,
    val overlapMs: Long = 500
)
