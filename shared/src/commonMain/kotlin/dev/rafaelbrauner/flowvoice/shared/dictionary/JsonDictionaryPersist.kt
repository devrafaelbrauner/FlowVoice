package dev.rafaelbrauner.flowvoice.shared.dictionary

import dev.rafaelbrauner.flowvoice.shared.persist.RawKeyValue
import dev.rafaelbrauner.flowvoice.shared.persist.backUp
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// N3 (revisão de código): os termos eram um StringSet, que não guarda ordem, e o
// `DictionaryApplier` desempata pela ordem de aprovação quando dois termos soam iguais. Agora é
// uma lista JSON. O StringSet antigo é migrado na primeira leitura: a ordem original já se perdeu,
// então vai em ordem alfabética (ao menos a mesma em todo início), e o conjunto antigo só é apagado
// depois que a lista foi gravada. JSON ilegível vai para backup (Y8) e não é sobrescrito sem ele.
//
// Revisão do motor (2026-10-01): aprovados, regras errado→correto e pendentes passam a gravar num
// único documento `{"approved":[...],"corrections":[["wrong","right"],...],"pending":[...]}`, de
// uma vez: qualquer mutação escreve os três estados, então nunca fica um arquivo pela metade. O
// arquivo do N3 (lista simples de strings) continua sendo lido, como aprovados.
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

    // O par de correção grava como ["wrong","right"]: par posicional dentro do mesmo documento.
    @Serializable
    private data class SnapshotJson(
        val approved: List<String> = emptyList(),
        val corrections: List<List<String>> = emptyList(),
        val pending: List<String> = emptyList()
    )

    private val snapshotSerializer = SnapshotJson.serializer()
    private val stringListSerializer = ListSerializer(String.serializer())

    override fun load(): DictionarySnapshot {
        val raw = store.get(key)
        if (raw != null) {
            return runCatching { decodeSnapshot(raw) }.getOrElse { error ->
                val backup = store.backUp(key, raw)
                log("dictionary_unreadable ${error::class.simpleName} backup=$backup")
                DictionarySnapshot()
            }
        }
        val old = legacy.read() ?: return DictionarySnapshot()
        val migrated = old.sortedWith(String.CASE_INSENSITIVE_ORDER.thenBy { it })
        save(approved = migrated, corrections = emptyList(), pending = emptyList())
        legacy.clear()
        return DictionarySnapshot(approved = migrated)
    }

    override fun save(approved: List<String>, corrections: List<CorrectionPair>, pending: List<String>) {
        store.put(
            key,
            Json.encodeToString(
                snapshotSerializer,
                SnapshotJson(
                    approved = approved,
                    corrections = corrections.map { listOf(it.wrong, it.right) },
                    pending = pending
                )
            )
        )
    }

    private fun decodeSnapshot(raw: String): DictionarySnapshot =
        runCatching {
            val parsed = Json.decodeFromString(snapshotSerializer, raw)
            DictionarySnapshot(
                approved = parsed.approved,
                corrections = parsed.corrections.map { pair ->
                    // Posicional: [0] é o errado, [1] o certo. Faltando um lado o documento está
                    // corrompido por inteiro e vai para o backup, não pela metade.
                    CorrectionPair(wrong = pair[0], right = pair[1])
                },
                pending = parsed.pending
            )
        }.recoverCatching {
            // N3: o arquivo anterior era uma lista simples de strings — então só aprovados tinham.
            DictionarySnapshot(approved = Json.decodeFromString(stringListSerializer, raw))
        }.getOrThrow()
}