package dev.rafaelbrauner.flowvoice.shared.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamRouterTest {

    @Test
    fun feedBeforeOpenIsDropped() = runTest {
        val router = ChannelStreamRouter()
        assertFalse(router.isOpen)
        router.feed(floatArrayOf(0.1f))
    }

    @Test
    fun openTakeClearLifecycle() = runTest {
        val router = ChannelStreamRouter()
        val channel = router.open()
        assertTrue(router.isOpen)
        router.feed(floatArrayOf(0.1f, 0.2f))
        val first = channel.receive()
        assertTrue(first is StreamCmd.Feed)
        val taken = router.take()
        assertNotNull(taken)
        assertFalse(router.isOpen)
        router.clear()
        assertFalse(router.isOpen)
    }

    @Test
    fun finalizeWithoutStreamReturnsNull() = runTest {
        val router = ChannelStreamRouter()
        assertNull(router.take())
    }

    @Test
    fun feedFinalizeKeepFifoOrder() = runTest {
        val router = ChannelStreamRouter()
        val channel = router.open()
        router.feed(floatArrayOf(0.1f))
        router.feed(floatArrayOf(0.2f))
        val reply = CompletableDeferred<FinalizedStreamText?>()
        channel.send(StreamCmd.Finalize(reply))
        val taken = router.take()
        assertNotNull(taken)
        val first = channel.receive()
        val second = channel.receive()
        val third = channel.receive()
        assertTrue(first is StreamCmd.Feed)
        assertTrue(second is StreamCmd.Feed)
        assertTrue(third is StreamCmd.Finalize)
        reply.complete(null)
    }

    @Test
    fun onlyOneChannelAtATime() = runTest {
        val router = ChannelStreamRouter()
        val first = router.open()
        assertTrue(router.isOpen)
        val second = router.open()
        assertTrue(router.isOpen)
        first.close()
        second.close()
        router.clear()
        assertFalse(router.isOpen)
        assertEquals(Channel<StreamCmd>(Channel.UNLIMITED).toString().take(7), Channel<StreamCmd>(Channel.UNLIMITED).toString().take(7))
    }
}
