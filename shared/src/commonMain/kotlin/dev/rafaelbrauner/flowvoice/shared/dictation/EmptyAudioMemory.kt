package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.math.max
import kotlin.math.min

// Memória do que já se provou vazio nesta sessão (P153). No S26 (2026-09-16 11:04), num ditado
// sussurrado, duas janelas com voz medida bem acima do limiar da P151 (`voicedMs=840` e `800`)
// foram transcritas e voltaram com `chars=0`: o limiar de fala do endpointer (`max(250, 3 × piso)`,
// com piso 16) é baixo o bastante para respiração e ruído de sala cruzarem, e só o modelo sabe que
// ali não havia palavra.
//
// Em vez de adivinhar um limiar novo — o jeito de quebrar quem fala baixo (P124) —, a sessão
// aprende com a resposta que já foi paga: áudio que não é mais alto do que um que voltou vazio não
// tem por que ser pago de novo. E o nível que já rendeu texto nesta sessão nunca é tomado por
// silêncio, mesmo depois de uma janela vazia mais alta, porque quem fala baixo tem de continuar
// sendo transcrito.
class EmptyAudioMemory {
    private var loudestEmpty: Int? = null
    private var quietestSpoken: Int? = null

    fun remember(peak: Int?, hasText: Boolean) {
        // Sem pico medido não há o que aprender: a janela não ensina nada sobre nível nenhum.
        val level = peak ?: return
        if (hasText) {
            quietestSpoken = quietestSpoken?.let { min(it, level) } ?: level
        } else {
            loudestEmpty = loudestEmpty?.let { max(it, level) } ?: level
        }
    }

    fun skips(peak: Int?): Boolean {
        val level = peak ?: return false
        val empty = loudestEmpty ?: return false
        if (level > empty) return false
        val spoken = quietestSpoken ?: return true
        return level < spoken
    }

    fun clear() {
        loudestEmpty = null
        quietestSpoken = null
    }
}
