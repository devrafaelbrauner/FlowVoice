package dev.rafaelbrauner.flowvoice.shared.preview

data class LivePreview(
    val finalized: String,
    val provisional: String
) {
    val full: String
        get() = listOf(finalized, provisional).filter { it.isNotBlank() }.joinToString(" ")
}
