package dev.rafaelbrauner.flowvoice.shared.dictionary

class InMemoryPersonalDictionary(
    private val persist: DictionaryPersist = DictionaryPersist.NoOp
) : PersonalDictionary {
    private val approvedTerms = linkedMapOf<String, String>()
    private val pendingTerms = linkedMapOf<String, String>()

    init {
        persist.loadApproved().forEach { term ->
            approvedTerms[normalizeKey(term)] = term
        }
    }

    override fun approved(): List<DictionaryTerm> =
        approvedTerms.values.map { DictionaryTerm(it, approved = true) }

    override fun pending(): List<DictionaryTerm> =
        pendingTerms.values.map { DictionaryTerm(it, approved = false) }

    override fun approve(surface: String) {
        val trimmed = surface.trim()
        require(trimmed.isNotEmpty()) { "surface must not be blank" }
        val key = normalizeKey(trimmed)
        pendingTerms.remove(key)
        approvedTerms[key] = trimmed
        persist.saveApproved(approvedTerms.values.toList())
    }

    override fun reject(surface: String) {
        val key = normalizeKey(surface.trim())
        pendingTerms.remove(key)
        if (approvedTerms.remove(key) != null) {
            persist.saveApproved(approvedTerms.values.toList())
        }
    }

    override fun suggestFrom(text: String): List<String> {
        val known = approvedTerms.keys + pendingTerms.keys
        return TermSuggester.candidates(text)
            .filter { normalizeKey(it) !in known }
            .also { suggestions ->
                suggestions.forEach { pendingTerms.putIfAbsent(normalizeKey(it), it) }
            }
    }

    override fun apply(text: String): String = DictionaryApplier.apply(text, approvedTerms.values)

    private fun normalizeKey(value: String): String = value.lowercase()
}

interface DictionaryPersist {
    fun loadApproved(): List<String>
    fun saveApproved(terms: List<String>)

    companion object {
        val NoOp: DictionaryPersist = object : DictionaryPersist {
            override fun loadApproved(): List<String> = emptyList()
            override fun saveApproved(terms: List<String>) = Unit
        }
    }
}
