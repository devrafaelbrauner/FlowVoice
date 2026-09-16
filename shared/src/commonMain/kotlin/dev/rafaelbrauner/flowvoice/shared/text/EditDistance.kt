package dev.rafaelbrauner.flowvoice.shared.text

import kotlin.math.abs
import kotlin.math.min

// Distância de edição com orçamento: "estas duas palavras são a mesma, escrita um pouco diferente?".
// Nasceu no guard da revisão (P132) e passou a valer também para a emenda entre janelas (P150), onde
// o modelo devolve o contexto sobreposto com outras letras. Só a resposta sim/não interessa, então o
// cálculo pára de valer assim que passa do orçamento.
object EditDistance {
    fun within(a: String, b: String, budget: Int): Boolean {
        if (abs(a.length - b.length) > budget) return false
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(substitution, min(previous[j] + 1, current[j - 1] + 1))
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length] <= budget
    }
}
