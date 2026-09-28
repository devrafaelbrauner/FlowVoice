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
//
// Só se aprende perto do limiar de fala (R1). Uma tosse, um esbarrão no aparelho ou um soluço do
// modelo com voz de verdade voltavam vazios bem acima dele, e a memória passava a pular toda fala
// mais baixa pelo resto do ditado, sem aviso. O teto é `SILENCE_CEILING_FACTOR` vezes o limiar de
// fala da sessão: respiração e ruído de sala mal passam do limiar, e fala perto do microfone passa
// de mil. Um vazio acima do teto não ensina nada e é devolvido como suspeito, para quem chama
// avisar do trecho sem texto.
class EmptyAudioMemory {
    private var loudestEmpty: Int? = null
    private var quietestSpoken: Int? = null
    // Piso de ruído da última janela que trouxe um: a janela do fim (flush) não traz o seu.
    private var noiseFloor: Int? = null

    fun rememberSpoken(peak: Int?, noiseFloor: Int?) {
        noiseFloor?.let { this.noiseFloor = it }
        val level = peak ?: return
        quietestSpoken = quietestSpoken?.let { min(it, level) } ?: level
    }

    // Falso quando o vazio não pode ser silêncio: a janela era alta demais para isso.
    fun rememberEmpty(peak: Int?, noiseFloor: Int?): Boolean {
        noiseFloor?.let { this.noiseFloor = it }
        // Sem pico medido não há o que aprender: a janela não ensina nada sobre nível nenhum.
        val level = peak ?: return true
        if (level > silenceCeiling(this.noiseFloor)) return false
        loudestEmpty = loudestEmpty?.let { max(it, level) } ?: level
        return true
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
        noiseFloor = null
    }

    companion object {
        const val SILENCE_CEILING_FACTOR = 2

        // O limiar de fala é o do endpointer com a configuração padrão, a única em uso (Android e
        // desktop): `max(minSpeechLevel, speechFactor × piso)`.
        fun silenceCeiling(noiseFloor: Int?): Int {
            val speechLevel = max(
                SpeechEndpointing.MIN_SPEECH_LEVEL,
                ((noiseFloor ?: 0) * SpeechEndpointing.SPEECH_FACTOR).toInt()
            )
            return SILENCE_CEILING_FACTOR * speechLevel
        }
    }
}
