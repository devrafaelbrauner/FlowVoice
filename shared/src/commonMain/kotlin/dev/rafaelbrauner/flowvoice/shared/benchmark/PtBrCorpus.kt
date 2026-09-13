package dev.rafaelbrauner.flowvoice.shared.benchmark

object PtBrCorpus {
    val clips = listOf(
        BenchmarkReference(
            id = "clinico-exame",
            reference = "O médico pediu o exame de sangue para amanhã de manhã."
        ),
        BenchmarkReference(
            id = "consulta-horario",
            reference = "Preciso marcar consulta no posto de saúde às quatorze horas."
        ),
        BenchmarkReference(
            id = "sinais-vitais",
            reference = "A pressão estava cento e vinte por oitenta e o paciente está estável."
        )
    )
}

data class BenchmarkReference(
    val id: String,
    val reference: String
)
