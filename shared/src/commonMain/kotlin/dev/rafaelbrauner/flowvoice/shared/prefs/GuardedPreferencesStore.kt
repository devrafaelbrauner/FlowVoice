package dev.rafaelbrauner.flowvoice.shared.prefs

import dev.rafaelbrauner.flowvoice.shared.persist.RawKeyValue
import dev.rafaelbrauner.flowvoice.shared.persist.backUp
import kotlinx.serialization.json.Json

// Y8 (revisão de código): preferência que não decodifica volta ao padrão, mas o texto cru vai antes
// para um backup e o evento é registrado; a próxima gravação não apaga o que estava lá. Aqui a
// gravação continua liberada: recusar faria cada ajuste da tela sumir ao reabrir o app, e o
// original já está no backup.
class GuardedPreferencesStore(
    private val store: RawKeyValue,
    private val key: String,
    private val log: (String) -> Unit = {}
) : PreferencesStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): AppPreferences {
        val raw = store.get(key) ?: return AppPreferences()
        return runCatching { json.decodeFromString(AppPreferences.serializer(), raw) }.getOrElse { error ->
            val backup = store.backUp(key, raw)
            log("prefs_unreadable ${error::class.simpleName} backup=$backup")
            AppPreferences()
        }
    }

    override fun write(value: AppPreferences) {
        store.put(key, json.encodeToString(AppPreferences.serializer(), value))
    }
}
