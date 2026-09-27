package dev.rafaelbrauner.flowvoice.shared.notes

import kotlinx.serialization.Serializable

// `deletedAtMs` marca a nota apagada (lápide, Y7) para o sync não trazê-la de volta de outro
// aparelho. Tem default para as notas já gravadas sem o campo continuarem decodificando.
@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val updatedAtMs: Long,
    val deletedAtMs: Long? = null
) {
    val isDeleted: Boolean get() = deletedAtMs != null
}
