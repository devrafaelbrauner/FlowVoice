package dev.rafaelbrauner.flowvoice.shared.prefs

import dev.rafaelbrauner.flowvoice.shared.persist.MapRawKeyValue
import kotlin.test.Test
import kotlin.test.assertEquals

// Y8 (revisão de código): preferência ilegível voltava ao padrão e a próxima gravação apagava o original.
class GuardedPreferencesStoreTest {
    @Test
    fun unreadablePreferencesAreBackedUpBeforeTheNextWrite() {
        val raw = """{"proofreadingEnabled":"talvez"}"""
        val store = MapRawKeyValue("prefs_json" to raw)
        val events = mutableListOf<String>()
        val prefs = GuardedPreferencesStore(store, "prefs_json") { events += it }

        assertEquals(AppPreferences(), prefs.read())
        prefs.write(AppPreferences(proofreadingEnabled = true))

        assertEquals(raw, store.values["prefs_json.bak"])
        assertEquals(true, prefs.read().proofreadingEnabled)
        assertEquals(1, events.size)
    }
}
