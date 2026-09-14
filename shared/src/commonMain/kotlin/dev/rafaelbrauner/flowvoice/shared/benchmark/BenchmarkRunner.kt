package dev.rafaelbrauner.flowvoice.shared.benchmark

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionEventLog
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException

class BenchmarkRunner(
    private val client: TranscriptionClient,
    private val apiKeyProvider: () -> String?,
    private val models: List<String> = TranscriptionShortlist.roundOne,
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp
) {
    suspend fun run(
        clips: List<BenchmarkClip>,
        budget: BenchmarkBudget
    ): BenchmarkReport {
        require(clips.isNotEmpty()) { "clips must not be empty" }
        val scores = mutableListOf<ModelScore>()
        var spentUsd = 0.0
        var requests = 0
        var stoppedReason: String? = null

        modelLoop@ for (model in models) {
            if (requests >= budget.maxRequests) {
                stoppedReason = "requests"
                break
            }
            if (spentUsd >= budget.maxUsd) {
                stoppedReason = "usd"
                break
            }
            val clipScores = mutableListOf<ClipScore>()
            for (clip in clips) {
                if (requests >= budget.maxRequests) {
                    stoppedReason = "requests"
                    break@modelLoop
                }
                if (spentUsd >= budget.maxUsd) {
                    stoppedReason = "usd"
                    break@modelLoop
                }
                val apiKey = apiKeyProvider().orEmpty()
                if (apiKey.isBlank()) {
                    clipScores += ClipScore(
                        clipId = clip.id,
                        hypothesis = "",
                        wer = 1.0,
                        latencyMs = 0L,
                        costUsd = null,
                        errorKind = "invalid_key"
                    )
                    continue
                }
                requests++
                eventLog.log(
                    "benchmark_request",
                    mapOf(
                        "model" to model,
                        "clip" to clip.id,
                        "durationMs" to clip.window.durationMs.toString()
                    )
                )
                val mark = TimeSource.Monotonic.markNow()
                val clipScore = try {
                    val result = client.transcribe(clip.window, apiKey, model)
                    val latency = mark.elapsedNow().inWholeMilliseconds
                    val cost = result.costUsd
                    if (cost != null) spentUsd += cost
                    ClipScore(
                        clipId = clip.id,
                        hypothesis = result.text,
                        wer = WordErrorRate.score(clip.reference, result.text),
                        latencyMs = latency,
                        costUsd = cost
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: TranscriptionError) {
                    ClipScore(
                        clipId = clip.id,
                        hypothesis = "",
                        wer = 1.0,
                        latencyMs = mark.elapsedNow().inWholeMilliseconds,
                        costUsd = null,
                        errorKind = error.kind
                    )
                } catch (_: Throwable) {
                    ClipScore(
                        clipId = clip.id,
                        hypothesis = "",
                        wer = 1.0,
                        latencyMs = mark.elapsedNow().inWholeMilliseconds,
                        costUsd = null,
                        errorKind = "network"
                    )
                }
                clipScores += clipScore
                eventLog.log(
                    "benchmark_clip",
                    mapOf(
                        "model" to model,
                        "clip" to clip.id,
                        "wer" to clipScore.wer.toString(),
                        "latencyMs" to clipScore.latencyMs.toString(),
                        "kind" to (clipScore.errorKind ?: "ok")
                    )
                )
            }
            scores += summarize(model, clipScores)
        }

        val defaultModel = pickDefault(scores)
        val fallbackModel = pickFallback(scores, defaultModel)
        return BenchmarkReport(
            models = scores.sortedWith(modelComparator()),
            defaultModel = defaultModel,
            fallbackModel = fallbackModel,
            spentUsd = spentUsd,
            requests = requests,
            stoppedReason = stoppedReason
        )
    }

    private fun summarize(model: String, clips: List<ClipScore>): ModelScore {
        val ok = clips.filter { it.errorKind == null }
        return ModelScore(
            model = model,
            clips = clips,
            meanWer = ok.takeIf { it.isNotEmpty() }?.map { it.wer }?.average(),
            meanLatencyMs = ok.takeIf { it.isNotEmpty() }?.map { it.latencyMs }?.average()?.toLong(),
            totalCostUsd = clips.mapNotNull { it.costUsd }.sum(),
            successes = ok.size,
            failures = clips.size - ok.size
        )
    }

    private fun pickDefault(scores: List<ModelScore>): String? =
        scores.filter { it.successes > 0 }
            .minWithOrNull(modelComparator())
            ?.model

    private fun pickFallback(scores: List<ModelScore>, defaultModel: String?): String? =
        scores.filter { it.successes > 0 && it.model != defaultModel }
            .minByOrNull { it.meanLatencyMs ?: Long.MAX_VALUE }
            ?.model

    private fun modelComparator(): Comparator<ModelScore> =
        compareBy<ModelScore> { it.meanWer ?: Double.POSITIVE_INFINITY }
            .thenBy { it.meanLatencyMs ?: Long.MAX_VALUE }
            .thenBy { it.totalCostUsd }
}
