package dev.rafaelbrauner.flowvoice.shared.text

import kotlin.math.min

// P156: diferença nas primeiras 4 letras é prefixo (ex-/im-, hiper-/hipo-, amox-/ampi-), não
// ortografia. "leucocitose"/"leucositose" (miolo) não contrasta. Recebe palavras já normalizadas;
// `minLength` é o tamanho da palavra maior a partir do qual a regra vale.
object OnsetContrast {
    const val ONSET_CHARS = 4

    fun has(a: String, b: String, minLength: Int): Boolean {
        if (maxOf(a.length, b.length) < minLength) return false
        val n = min(ONSET_CHARS, min(a.length, b.length))
        for (i in 0 until n) {
            if (a[i] != b[i]) return true
        }
        return false
    }
}
