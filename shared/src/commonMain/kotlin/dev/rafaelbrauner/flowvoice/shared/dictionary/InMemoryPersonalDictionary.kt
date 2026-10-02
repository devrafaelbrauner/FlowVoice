package dev.rafaelbrauner.flowvoice.shared.dictionary

// N9 (revisão de código): toda palavra de 4+ letras de todo ditado virava sugestão pendente, e a
// lista crescia sem limite, guardando vocabulário clínico na memória. Ficam as últimas
// MAX_PENDING_SUGGESTIONS; ao passar disso sai a mais antiga.
//
// Revisão do motor (2026-10-01): os pendentes agora sobrevivem ao reinício — eram só de memória e o
// usuário perdia a lista que ainda não julgou. E o usuário pode ensinar regras errado→correto
// (learnCorrection), que valem palavra inteira antes do casamento por som.
class InMemoryPersonalDictionary(
    private val persist: DictionaryPersist = DictionaryPersist.NoOp
) : PersonalDictionary {
    private val approvedTerms = linkedMapOf<String, String>()
    private val pendingTerms = linkedMapOf<String, String>()

    // A chave é o `wrong` em minúsculas (a regra casa sem caixa) e o valor guarda o par como o
    // usuário o ensinou, para listar e persistir com fidelidade. A ordem de inserção é a ordem de
    // aprendizado; reaprender o mesmo `wrong` substitui o par no lugar.
    private val correctionRules = linkedMapOf<String, CorrectionPair>()

    // Contador de mutação para quem memoiza o resultado de apply (a prévia incremental). O acesso é
    // da thread principal, como todo o estado desta classe, então um var simples basta.
    private var revisionCounter = 0L

    init {
        val snapshot = persist.load()
        snapshot.approved.forEach { term -> approvedTerms[normalizeKey(term)] = term }
        snapshot.pending.forEach { term -> pendingTerms[normalizeKey(term)] = term }
        snapshot.corrections.forEach { rule -> correctionRules[rule.wrong.lowercase()] = rule }
    }

    override fun approved(): List<DictionaryTerm> =
        approvedTerms.values.map { DictionaryTerm(it, approved = true) }

    override fun pending(): List<DictionaryTerm> =
        pendingTerms.values.map { DictionaryTerm(it, approved = false) }

    override fun corrections(): List<CorrectionPair> = correctionRules.values.toList()

    override fun approve(surface: String) {
        val trimmed = surface.trim()
        require(trimmed.isNotEmpty()) { "surface must not be blank" }
        val key = normalizeKey(trimmed)
        pendingTerms.remove(key)
        approvedTerms[key] = trimmed
        persistSnapshot()
        revisionCounter++
    }

    override fun reject(surface: String) {
        val key = normalizeKey(surface.trim())
        pendingTerms.remove(key)
        if (approvedTerms.remove(key) != null) {
            persistSnapshot()
        }
        // A rejeição também pode ter tirado um pendente: revision conta a chamada de mutação, e
        // esbarrar no estado é o que interessa ao consumidor da prévia.
        revisionCounter++
    }

    override fun learnCorrection(wrong: String, right: String) {
        require(isLearnableWord(wrong)) { "wrong must be a single word with at least two letters" }
        require(isLearnableWord(right)) { "right must be a single word with at least two letters" }
        // Só diferença de caixa não é correção ("Caza"→"casa" é a mesma palavra); diferença de
        // acento vale, porque "nao"→"não" é justamente o erro que o usuário quer ver sumir.
        require(!wrong.equals(right, ignoreCase = true)) { "right must differ from wrong beyond case" }
        // A regra mais recente de um mesmo `wrong` é a que vale: o usuário corrigiu a correção.
        correctionRules[wrong.lowercase()] = CorrectionPair(wrong, right)
        while (correctionRules.size > MAX_CORRECTION_RULES) {
            correctionRules.remove(correctionRules.keys.first())
        }
        // O lado certo entra aprovado — a regra existe porque o usuário escreve `right` de verdade.
        // approve já grava o instantâneo inteiro (aprovados, regras e pendentes) e sobe a revisão;
        // o aprendizado da regra em si é uma segunda mutação e conta mais um.
        approve(right)
        revisionCounter++
    }

    override fun forgetCorrection(wrong: String) {
        // Esquecer a regra não rebaixa o `right`: ele segue aprovado no vocabulário.
        if (correctionRules.remove(wrong.lowercase()) != null) {
            persistSnapshot()
        }
        revisionCounter++
    }

    override fun suggestFrom(text: String): List<String> {
        val known = approvedTerms.keys + pendingTerms.keys
        // Teto de novas sugestões por chamada (revisão do motor, Item 3): um ditado longo não pode
        // inundar a lista de pendentes de uma vez; valem as primeiras candidatas novas na ordem do
        // texto, as demais esperam o próximo ditado.
        val suggestions = TermSuggester.candidates(text)
            .filter { normalizeKey(it) !in known }
            .take(MAX_NEW_SUGGESTIONS)
        var changed = false
        suggestions.forEach { surface ->
            if (pendingTerms.putIfAbsent(normalizeKey(surface), surface) == null) changed = true
        }
        while (pendingTerms.size > MAX_PENDING_SUGGESTIONS) {
            pendingTerms.remove(pendingTerms.keys.first())
            changed = true
        }
        // Só grava e move a revisão quando o estado mudou: o mesmo ditado repetido não é mutação.
        if (changed) {
            persistSnapshot()
            revisionCounter++
        }
        return suggestions
    }

    override fun revision(): Long = revisionCounter

    override fun apply(text: String): String =
        DictionaryApplier.apply(text, approvedTerms.values, correctionRules.values)

    // Palavra aprendível: só letras cobrindo o texto inteiro (sem espaço, hífen ou dígito) e no
    // mínimo duas letras — "é" sozinho não ensina correção nenhuma.
    private fun isLearnableWord(value: String): Boolean =
        value.matches(WORD_ONLY) && value.length >= MIN_CORRECTION_LETTERS

    private fun persistSnapshot() {
        persist.save(
            approved = approvedTerms.values.toList(),
            corrections = correctionRules.values.toList(),
            pending = pendingTerms.values.toList()
        )
    }

    private fun normalizeKey(value: String): String = value.lowercase()

    companion object {
        const val MAX_PENDING_SUGGESTIONS = 100
        const val MAX_CORRECTION_RULES = 200
        const val MAX_NEW_SUGGESTIONS = 12
        const val MIN_CORRECTION_LETTERS = 2

        private val WORD_ONLY = Regex("[\\p{L}]+")
    }
}

// Instantâneo completo do dicionário num único documento. Gravar os três estados de uma vez evita
// que aprovar um termo sobrescreva o arquivo sem as regras ou os pendentes: o save antigo gravava
// só os aprovados, e o resto do estado vivia só em memória.
data class DictionarySnapshot(
    val approved: List<String> = emptyList(),
    val corrections: List<CorrectionPair> = emptyList(),
    val pending: List<String> = emptyList()
)

interface DictionaryPersist {
    fun load(): DictionarySnapshot
    fun save(approved: List<String>, corrections: List<CorrectionPair>, pending: List<String>)

    companion object {
        val NoOp: DictionaryPersist = object : DictionaryPersist {
            override fun load(): DictionarySnapshot = DictionarySnapshot()
            override fun save(approved: List<String>, corrections: List<CorrectionPair>, pending: List<String>) = Unit
        }
    }
}