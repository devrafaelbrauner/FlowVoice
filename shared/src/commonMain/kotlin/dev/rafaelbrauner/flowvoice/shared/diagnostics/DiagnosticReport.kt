package dev.rafaelbrauner.flowvoice.shared.diagnostics

data class DiagnosticReport(
    val generatedAtMs: Long,
    val lines: List<String>
) {
    fun asText(): String = lines.joinToString("\n")

    companion object {
        fun build(
            generatedAtMs: Long,
            accessibility: Boolean,
            microphoneGranted: Boolean,
            keyConfigured: Boolean,
            signedIn: Boolean,
            proofreading: Boolean,
            model: String,
            notes: Int,
            terms: Int,
            overlay: Boolean
        ): DiagnosticReport = DiagnosticReport(
            generatedAtMs = generatedAtMs,
            lines = listOf(
                "FlowVoice diagnóstico",
                "acessibilidade=$accessibility",
                "microfone=$microphoneGranted",
                "chave_openrouter=${if (keyConfigured) "sim" else "nao"}",
                "google=$signedIn",
                "revisao=$proofreading",
                "modelo=$model",
                "notas=$notes",
                "termos=$terms",
                "overlay=$overlay"
            )
        )
    }
}
