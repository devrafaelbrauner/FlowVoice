package dev.rafaelbrauner.flowvoice.shared.dictionary

import dev.rafaelbrauner.flowvoice.shared.persist.MapRawKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// N3 (revisão de código): o StringSet perdia a ordem de aprovação que o `DictionaryApplier` usa
// para desempatar termos que soam iguais.
class JsonDictionaryPersistTest {
    private class Legacy(var terms: Set<String>?) : JsonDictionaryPersist.LegacyTerms {
        override fun read(): Set<String>? = terms
        override fun clear() {
            terms = null
        }
    }

    @Test
    fun approvalOrderSurvivesARestart() {
        val store = MapRawKeyValue()
        val first = InMemoryPersonalDictionary(JsonDictionaryPersist(store, KEY, Legacy(null)))
        listOf("Zolpidem", "Grandemont", "dispneia", "Atenolol").forEach(first::approve)

        val reopened = InMemoryPersonalDictionary(JsonDictionaryPersist(store, KEY, Legacy(null)))

        assertEquals(listOf("Zolpidem", "Grandemont", "dispneia", "Atenolol"), reopened.approved().map { it.surface })
    }

    @Test
    fun theLegacyStringSetIsMigratedOnceInADeterministicOrder() {
        val store = MapRawKeyValue()
        val legacy = Legacy(setOf("dispneia", "Atenolol", "Grandemont"))

        val migrated = JsonDictionaryPersist(store, KEY, legacy).load().approved

        assertEquals(listOf("Atenolol", "dispneia", "Grandemont"), migrated)
        assertNull(legacy.terms)
        assertEquals(migrated, JsonDictionaryPersist(store, KEY, Legacy(null)).load().approved)
    }

    @Test
    fun unreadableTermsAreBackedUp() {
        val store = MapRawKeyValue(KEY to "[\"Atenolol\"")

        assertEquals(emptyList(), JsonDictionaryPersist(store, KEY, Legacy(setOf("velho"))).load().approved)
        assertEquals("[\"Atenolol\"", store.values["$KEY.bak"])
    }

    // Revisão do motor (2026-10-01): aprovados, regras e pendentes agora são um documento só, e o
    // reinício devolve os três — inclusive os pendentes que o usuário ainda não julgou.
    @Test
    fun correctionsAndPendingSurviveARestart() {
        val store = MapRawKeyValue()
        val first = InMemoryPersonalDictionary(JsonDictionaryPersist(store, KEY, Legacy(null)))
        first.approve("Atenolol")
        first.learnCorrection("caza", "casa")
        first.suggestFrom("paciente caminha muito")

        val reopened = InMemoryPersonalDictionary(JsonDictionaryPersist(store, KEY, Legacy(null)))

        assertEquals(listOf("Atenolol", "casa"), reopened.approved().map { it.surface })
        assertEquals(listOf(CorrectionPair("caza", "casa")), reopened.corrections())
        assertEquals(listOf("paciente", "caminha"), reopened.pending().map { it.surface })
    }

    // O documento gravado tem a forma acordada: aprovados, pares de correção posicionais e
    // pendentes, num único JSON.
    @Test
    fun theSnapshotIsSavedInTheAgreedDocumentShape() {
        val store = MapRawKeyValue()

        JsonDictionaryPersist(store, KEY, Legacy(null))
            .save(listOf("Atenolol"), listOf(CorrectionPair("caza", "casa")), listOf("paciente"))

        assertEquals(
            "{\"approved\":[\"Atenolol\"],\"corrections\":[[\"caza\",\"casa\"]],\"pending\":[\"paciente\"]}",
            store.values[KEY]
        )
        val snapshot = JsonDictionaryPersist(store, KEY, Legacy(null)).load()
        assertEquals(listOf("Atenolol"), snapshot.approved)
        assertEquals(listOf(CorrectionPair("caza", "casa")), snapshot.corrections)
        assertEquals(listOf("paciente"), snapshot.pending)
    }

    // O arquivo do N3 era uma lista simples de strings — então só aprovados existiam, e é assim que
    // ele continua sendo lido, com regras e pendentes nascendo vazios.
    @Test
    fun theOldPlainListFileIsReadAsApprovedTerms() {
        val store = MapRawKeyValue(KEY to "[\"Atenolol\",\"dispneia\"]")
        val legacy = Legacy(setOf("velho"))

        val snapshot = JsonDictionaryPersist(store, KEY, legacy).load()

        assertEquals(listOf("Atenolol", "dispneia"), snapshot.approved)
        assertTrue(snapshot.corrections.isEmpty())
        assertTrue(snapshot.pending.isEmpty())
        // Com arquivo presente o StringSet nem é consultado: o que está gravado vence.
        assertEquals(setOf("velho"), legacy.terms)
    }

    // Documento novo corrompido (par de correção sem os dois lados) vai inteiro para o backup e
    // volta como dicionário vazio, o mesmo padrão do arquivo ilegível do N3.
    @Test
    fun aCorruptSnapshotDocumentIsBackedUpWhole() {
        val store = MapRawKeyValue(KEY to "{\"approved\":[\"Atenolol\"],\"corrections\":[[\"caza\"]]}")

        assertEquals(DictionarySnapshot(), JsonDictionaryPersist(store, KEY, Legacy(null)).load())
        assertEquals(
            "{\"approved\":[\"Atenolol\"],\"corrections\":[[\"caza\"]]}",
            store.values["$KEY.bak"]
        )
    }

    private companion object {
        const val KEY = "approved_terms_json"
    }
}