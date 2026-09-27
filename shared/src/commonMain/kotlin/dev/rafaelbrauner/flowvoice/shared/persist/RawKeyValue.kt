package dev.rafaelbrauner.flowvoice.shared.persist

// Texto cru por chave (SharedPreferences no Android). Separa a regra de não perder dado gravado
// (Y8) do armazenamento, para testar sem aparelho.
interface RawKeyValue {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

// Guarda o texto que não decodificou numa chave de backup livre ("x.bak", "x.bak2", …) sem
// sobrescrever um backup anterior diferente. Devolve a chave usada.
fun RawKeyValue.backUp(key: String, raw: String): String {
    var index = 1
    while (true) {
        val candidate = if (index == 1) "$key.bak" else "$key.bak$index"
        val existing = get(candidate)
        if (existing == null || existing == raw) {
            if (existing == null) put(candidate, raw)
            return candidate
        }
        index++
    }
}
