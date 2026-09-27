package dev.rafaelbrauner.flowvoice.shared.dictionary

import dev.rafaelbrauner.flowvoice.shared.persist.MapRawKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        val migrated = JsonDictionaryPersist(store, KEY, legacy).loadApproved()

        assertEquals(listOf("Atenolol", "dispneia", "Grandemont"), migrated)
        assertNull(legacy.terms)
        assertEquals(migrated, JsonDictionaryPersist(store, KEY, Legacy(null)).loadApproved())
    }

    @Test
    fun unreadableTermsAreBackedUp() {
        val store = MapRawKeyValue(KEY to "[\"Atenolol\"")

        assertEquals(emptyList(), JsonDictionaryPersist(store, KEY, Legacy(setOf("velho"))).loadApproved())
        assertEquals("[\"Atenolol\"", store.values["$KEY.bak"])
    }

    private companion object {
        const val KEY = "approved_terms_json"
    }
}
