package dev.rafaelbrauner.flowvoice.shared.dictionary

import dev.rafaelbrauner.flowvoice.shared.persist.RawKeyValue
import dev.rafaelbrauner.flowvoice.shared.persist.backUp
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// N3 (revisão de código): os termos eram um StringSet, que não guarda ordem, e o
// `DictionaryApplier` desempata pela ordem de aprovação quando dois termos soam iguais. Agora é
// uma lista JSON. O StringSet antigo é migrado na primeira leitura: a ordem original já se perdeu,
// então vai em ordem alfabética (ao menos a mesma em todo início), e o conjunto antigo só é apagado
// depois que a lista foi gravada. JSON ilegível vai para backup (Y8) e não é sobrescrito sem ele.
class JsonDictionaryPersist(
    private val store: RawKeyValue,
    private val key: String,
    private val legacy: LegacyTerms,
    private val log: (String) -> Unit = {}
) : DictionaryPersist {
    interface LegacyTerms {
        fun read(): Set<String>?
        fun clear()
    }

    private val serializer = ListSerializer(String.serializer())

    override fun loadApproved(): List<String> {
        val raw = store.get(key)
        if (raw != null) {
            return runCatching { Json.decodeFromString(serializer, raw) }.getOrElse { error ->
                val backup = store.backUp(key, raw)
                log("dictionary_unreadable ${error::class.simpleName} backup=$backup")
                emptyList()
            }
        }
        val old = legacy.read() ?: return emptyList()
        val migrated = old.sortedWith(String.CASE_INSENSITIVE_ORDER.thenBy { it })
        saveApproved(migrated)
        legacy.clear()
        return migrated
    }

    override fun saveApproved(terms: List<String>) {
        store.put(key, Json.encodeToString(serializer, terms))
    }
}
