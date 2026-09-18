package dev.rafaelbrauner.flowvoice.shared.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

data class FinalizedStreamText(
    val text: String,
    val audio: FloatArray,
    val sampleRateHz: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FinalizedStreamText) return false
        return text == other.text &&
            sampleRateHz == other.sampleRateHz &&
            audio.contentEquals(other.audio)
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + audio.contentHashCode()
        result = 31 * result + sampleRateHz
        return result
    }
}

sealed interface StreamCmd {
    data class Feed(val pcm: FloatArray) : StreamCmd {
        override fun equals(other: Any?): Boolean =
            other is Feed && pcm.contentEquals(other.pcm)
        override fun hashCode(): Int = pcm.contentHashCode()
    }
    data class Finalize(val reply: CompletableDeferred<FinalizedStreamText?>) : StreamCmd
    data class Snapshot(val reply: CompletableDeferred<FloatArray>) : StreamCmd
    data object Cancel : StreamCmd
}

enum class SessionState {
    IDLE,
    LISTENING,
    WORKING
}

enum class StreamPhase {
    LISTENING,
    WORKING
}

enum class StreamWorkKind {
    TRANSCRIBING,
    POLISHING
}

data class StreamText(
    val committed: String,
    val tentative: String
)

interface StreamRouter {
    val isOpen: Boolean
    fun open(): Channel<StreamCmd>
    fun take(): Channel<StreamCmd>?
    fun current(): Channel<StreamCmd>?
    fun clear()
    fun feed(frame: FloatArray)
}
