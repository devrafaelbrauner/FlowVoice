package dev.rafaelbrauner.flowvoice.shared.pipeline

fun interface TextInserter {
    fun insert(text: String): InsertOutcome
}

data class InsertOutcome(
    val inserted: Boolean,
    val summary: String
)
