package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.transcription.IncrementalTranscriptionController
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

// Público para o desktop avisar do trecho que falhou como o Android (Y6).
class TranscriptionFailureSummary private constructor(
    val failedCount: Int,
    private val failedWindows: List<Int>,
    private val totalWindows: Int,
    private val reasons: List<String>
) {
    val partialMessage: String
        get() = "${windowsPhrase()} (${reasons.joinToString(", ")}): texto incompleto"

    val noTextMessage: String
        get() = "Nenhum trecho transcrito (${reasons.joinToString(", ")})"

    private fun windowsPhrase(): String =
        if (failedWindows.size == 1) {
            "Trecho ${failedWindows.single()} de $totalWindows falhou"
        } else {
            "Trechos ${failedWindows.joinToString(", ")} de $totalWindows falharam"
        }

    companion object {
        // Janela sem resposta quando o prazo do fim do ditado venceu (Y5).
        const val DEADLINE_KIND = "finalize_deadline"

        fun from(segments: List<TranscriptionSegment>, totalWindows: Int): TranscriptionFailureSummary? {
            val failed = segments
                .filter { it.status == TranscriptionSegment.Status.Failed }
                .sortedBy { it.windowIndex }
            if (failed.isEmpty()) return null
            return TranscriptionFailureSummary(
                failedCount = failed.size,
                failedWindows = failed.map { it.windowIndex + 1 },
                totalWindows = maxOf(totalWindows, segments.size),
                reasons = failed.map { describe(it.errorKind) }.distinct()
            )
        }

        private fun describe(kind: String?): String = when {
            kind == "timeout" -> "timeout"
            kind == DEADLINE_KIND -> "sem resposta a tempo"
            kind == "network" -> "sem rede"
            kind == "rate_limit" -> "limite de requisições"
            kind == "invalid_key" -> "chave OpenRouter ausente ou inválida"
            kind == "forbidden" -> "recusado pela OpenRouter"
            kind == "budget" -> "teto de requisições da sessão"
            kind == IncrementalTranscriptionController.EMPTY_VOICE_KIND -> "voz sem texto"
            kind?.startsWith("server") == true -> "erro do servidor"
            else -> "resposta inválida"
        }
    }
}
