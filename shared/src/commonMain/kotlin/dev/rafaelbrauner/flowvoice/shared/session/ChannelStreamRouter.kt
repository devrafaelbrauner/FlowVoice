package dev.rafaelbrauner.flowvoice.shared.session

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean

class ChannelStreamRouter : StreamRouter {

    private val lock = Any()
    private var channel: Channel<StreamCmd>? = null
    private val openFlag = AtomicBoolean(false)

    override val isOpen: Boolean get() = openFlag.get()

    override fun open(): Channel<StreamCmd> {
        val fresh = Channel<StreamCmd>(Channel.UNLIMITED)
        synchronized(lock) { channel = fresh }
        openFlag.set(true)
        return fresh
    }

    override fun take(): Channel<StreamCmd>? {
        openFlag.set(false)
        synchronized(lock) {
            val current = channel
            channel = null
            return current
        }
    }

    override fun current(): Channel<StreamCmd>? =
        synchronized(lock) { channel }

    override fun clear() {
        openFlag.set(false)
        synchronized(lock) { channel = null }
    }

    override fun feed(frame: FloatArray) {
        if (!openFlag.get()) return
        val current = synchronized(lock) { channel } ?: return
        current.trySend(StreamCmd.Feed(frame.copyOf()))
    }
}
