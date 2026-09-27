package dev.rafaelbrauner.flowvoice.shared.persist

class MapRawKeyValue(vararg entries: Pair<String, String>) : RawKeyValue {
    val values = linkedMapOf(*entries)

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }
}
