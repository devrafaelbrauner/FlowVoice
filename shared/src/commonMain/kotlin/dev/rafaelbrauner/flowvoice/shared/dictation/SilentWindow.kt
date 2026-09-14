package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.math.abs

// Captura silenciada pelo sistema (app em segundo plano sem serviço de microfone, P121) chega como
// PCM zerado; enviá-la custa uma requisição e o modelo pode devolver texto inventado. O limiar
// (pico de 16, ~-66 dBFS) só pega silêncio digital: fala baixa e ruído de sala seguem para a API.
object SilentWindow {
    const val MAX_PEAK = 16

    fun detect(window: DictationWindow): Boolean {
        if (window.format.sampleBits != 16) return false
        val pcm = window.pcm
        var index = 0
        while (index + 1 < pcm.size) {
            val sample = ((pcm[index].toInt() and 0xFF) or (pcm[index + 1].toInt() shl 8)).toShort().toInt()
            if (abs(sample) > MAX_PEAK) return false
            index += 2
        }
        return true
    }
}
