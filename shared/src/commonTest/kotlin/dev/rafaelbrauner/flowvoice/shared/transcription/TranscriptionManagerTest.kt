package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTranscriptionApi(
    private val texts: List<String>,
    val seen: MutableList<TranscriptionRequest> = mutableListOf(),
    private val failWith: ((TranscriptionRequest) -> TranscriptionError?)? = null
) : TranscriptionApi {
    private var index = 0
    override suspend fun transcribe(request: TranscriptionRequest, apiKey: String): TranscriptionResult {
        seen.add(request)
        failWith?.invoke(request)?.let { throw it }
        val text = texts.getOrElse(index++) { texts.last() }
        assertTrue(apiKey.isNotBlank(), "fake exige chave não-vazia")
        return TranscriptionResult(text, OutputLanguage.PT, request.model, 10)
    }
}

class TranscriptionManagerTest {

    @Test
    fun splitWindowsRespectsOverlap() {
        val manager = managerWithFake(listOf("a"))
        val audio = FloatArray(16000 * 5)
        val windows = manager.splitWindows(audio, 16000 * 3, 16000 * 2)
        assertEquals(2, windows.size)
        assertEquals(16000 * 3, windows[0].size)
    }

    @Test
    fun mergeOverlapDedupsRepeatedRegion() {
        val manager = managerWithFake(listOf("a"))
        assertEquals(
            "bom dia tudo bem",
            manager.mergeOverlap("bom dia", "dia tudo bem")
        )
        assertEquals(
            "bom dia tudo bem",
            manager.mergeOverlap("bom dia", "tudo bem")
        )
        assertEquals("oi", manager.mergeOverlap("", "oi"))
        assertEquals("oi", manager.mergeOverlap("oi", ""))
    }

    @Test
    fun threeWindowsMergeWithoutDuplication() = runTest {
        val fake = FakeTranscriptionApi(listOf("bom dia", "dia tudo", "tudo bem"))
        val manager = TranscriptionManager(
            router = dev.rafaelbrauner.flowvoice.shared.session.ChannelStreamRouter(),
            api = fake,
            config = dev.rafaelbrauner.flowvoice.shared.session.DictationConfig(
                sampleRateHz = 1000,
                windowMs = 3000,
                overlapMs = 1000
            ),
            scope = kotlinx.coroutines.test.TestScope(testScheduler)
        )
        val audio = FloatArray(7000)
        val (text, report) = manager.transcribeWindows(audio, "key12345678")
        assertEquals("bom dia tudo bem", text)
        assertEquals(3, report.windows)
        assertEquals(3, fake.seen.size)
    }

    @Test
    fun longAudioSplitsAndMerges() = runTest {
        val fake = FakeTranscriptionApi(listOf("parte um", "um parte dois", "dois fim"))
        val manager = TranscriptionManager(
            router = dev.rafaelbrauner.flowvoice.shared.session.ChannelStreamRouter(),
            api = fake,
            config = dev.rafaelbrauner.flowvoice.shared.session.DictationConfig(
                sampleRateHz = 1000,
                windowMs = 3000,
                overlapMs = 1000
            ),
            scope = kotlinx.coroutines.test.TestScope(testScheduler)
        )
        val audio = FloatArray(7000)
        val (text, report) = manager.transcribeWindows(audio, "key12345678")
        assertEquals(3, report.windows)
        assertEquals("parte um parte dois fim", text)
        assertEquals(3, fake.seen.size)
    }

    @Test
    fun stopWithoutStartReturnsEmpty() = runTest {
        val manager = managerWithFake(listOf("a"))
        val (text, report) = manager.stop("key12345678")
        assertEquals("", text)
        assertNull(report)
    }

    @Test
    fun cancelResetsToIdle() = runTest {
        val router = dev.rafaelbrauner.flowvoice.shared.session.ChannelStreamRouter()
        val manager = TranscriptionManager(
            router = router,
            api = FakeTranscriptionApi(listOf("a")),
            scope = kotlinx.coroutines.test.TestScope(testScheduler)
        )
        manager.start()
        router.feed(floatArrayOf(0.1f))
        manager.cancel()
        assertEquals(dev.rafaelbrauner.flowvoice.shared.session.SessionState.IDLE, manager.state.value)
    }

    @Test
    fun snapshotReturnsAccumulatedAudioKeepsSession() = runTest {
        val router = dev.rafaelbrauner.flowvoice.shared.session.ChannelStreamRouter()
        val manager = TranscriptionManager(
            router = router,
            api = FakeTranscriptionApi(listOf("a")),
            scope = backgroundScope
        )
        manager.start()
        router.feed(floatArrayOf(0.1f, 0.2f))
        router.feed(floatArrayOf(0.3f))
        val copy = manager.snapshot()
        assertEquals(3, copy.size)
        assertTrue(router.isOpen)
        manager.cancel()
    }

    @Test
    fun publishTentativeKeepsCommitted() = runTest {
        val manager = managerWithFake(listOf("a"))
        manager.publishTentative("prov...")
        assertEquals("", manager.liveText.value.committed)
        assertEquals("prov...", manager.liveText.value.tentative)
    }

    @Test
    fun modelNotFoundFallsBackPerWindow() = runTest {
        val fake = FakeTranscriptionApi(
            listOf("texto ok"),
            failWith = { request ->
                if (request.model == "modelo-removido") TranscriptionError.ModelNotFound(request.model)
                else null
            }
        )
        val manager = TranscriptionManager(
            router = dev.rafaelbrauner.flowvoice.shared.session.ChannelStreamRouter(),
            api = fake,
            config = dev.rafaelbrauner.flowvoice.shared.session.DictationConfig(
                sampleRateHz = 1000,
                windowMs = 3000,
                overlapMs = 1000
            ),
            scope = kotlinx.coroutines.test.TestScope(testScheduler)
        )
        val (text, report) = manager.transcribeWindows(
            FloatArray(7000),
            "key12345678",
            model = "modelo-removido",
            fallbackModel = "modelo-bom"
        )
        assertTrue(text.isNotBlank())
        assertEquals(3, fake.seen.size / 2)
        assertTrue(report.modelId.contains("fallback de modelo-removido"))
    }

    private fun managerWithFake(texts: List<String>): TranscriptionManager =
        TranscriptionManager(
            router = dev.rafaelbrauner.flowvoice.shared.session.ChannelStreamRouter(),
            api = FakeTranscriptionApi(texts),
            scope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.TestScope().testScheduler)
        )
}
