package dev.rafaelbrauner.flowvoice.shared.notes

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val updatedAtMs: Long
)
