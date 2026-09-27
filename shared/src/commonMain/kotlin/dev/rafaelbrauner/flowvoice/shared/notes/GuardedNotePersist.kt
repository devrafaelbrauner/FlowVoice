package dev.rafaelbrauner.flowvoice.shared.notes

import dev.rafaelbrauner.flowvoice.shared.persist.RawKeyValue
import dev.rafaelbrauner.flowvoice.shared.persist.backUp
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray

// Y8 (revisão de código): antes, uma falha de decodificação virava lista vazia e o próximo `save`
// gravava por cima de todas as notas, sem log. Agora cada nota decodifica sozinha (uma nota ruim não
// derruba as outras) e o texto cru vai para um backup antes de qualquer gravação. Se o texto inteiro
// não é uma lista legível, nada é gravado por cima dele neste processo: as notas novas ficam só na
// memória e cada recusa é registrada. O log leva só o evento, nunca o texto clínico.
class GuardedNotePersist(
    private val store: RawKeyValue,
    private val key: String,
    private val log: (String) -> Unit = {}
) : NotePersist {
    private val json = Json { ignoreUnknownKeys = true }
    private var writable = true

    override fun load(): List<Note> {
        val raw = store.get(key) ?: return emptyList()
        val entries: JsonArray = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrElse { error ->
            writable = false
            val backup = store.backUp(key, raw)
            log("notes_unreadable ${error::class.simpleName} backup=$backup")
            return emptyList()
        }
        val notes = entries.mapNotNull { entry ->
            runCatching { json.decodeFromJsonElement(Note.serializer(), entry) }.getOrNull()
        }
        val dropped = entries.size - notes.size
        if (dropped > 0) {
            val backup = store.backUp(key, raw)
            log("notes_entries_unreadable count=$dropped backup=$backup")
        }
        return notes
    }

    override fun save(notes: List<Note>) {
        if (!writable) {
            log("notes_save_refused")
            return
        }
        store.put(key, json.encodeToString(ListSerializer(Note.serializer()), notes))
    }
}
