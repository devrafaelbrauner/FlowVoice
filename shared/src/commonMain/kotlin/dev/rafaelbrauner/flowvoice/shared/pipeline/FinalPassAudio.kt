package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.WindowCut

// O áudio do ditado para a passada final, montado das janelas que o pipeline já guarda da sessão (em
// memória, nunca em disco). Cada pedido de transcrição da OpenRouter tem ~60 s de processamento e 25 MB:
// o áudio longo vai em pedaços de até MAX_CHUNK_MS, e cada corte cai num fim de janela em silêncio —
// uma janela que fechou na pausa (`Pause`) ou logo antes da fala que voltou depois de silêncio
// (`Leading`). Sem silêncio na segunda metade do pedaço, o corte fica no último fim de janela que cabe:
// o corte do teto já é o trecho mais baixo perto do alvo, e nenhuma janela parte palavra no meio da fala
// por mais de uns milissegundos.
object FinalPassAudio {
    const val MAX_CHUNK_MS = 50_000L

    class Chunk(val pcm: ByteArray, val format: AudioFormat, val durationMs: Long)

    fun chunks(windows: List<DictationWindow>, maxChunkMs: Long = MAX_CHUNK_MS): List<Chunk> {
        val ordered = windows.sortedBy { it.index }.filter { it.pcm.isNotEmpty() }
        if (ordered.isEmpty()) return emptyList()
        val groups = mutableListOf<List<DictationWindow>>()
        var current = mutableListOf<DictationWindow>()
        var currentMs = 0L
        ordered.forEach { window ->
            while (current.isNotEmpty() && currentMs + window.durationMs > maxChunkMs) {
                val cut = cutPoint(current, maxChunkMs)
                groups += current.subList(0, cut).toList()
                current = current.subList(cut, current.size).toMutableList()
                currentMs = current.sumOf { it.durationMs }
            }
            current += window
            currentMs += window.durationMs
        }
        if (current.isNotEmpty()) groups += current
        return groups.map { group ->
            Chunk(
                pcm = concat(group.map { it.pcm }),
                format = group.first().format,
                durationMs = group.sumOf { it.durationMs }
            )
        }
    }

    // Quantas janelas do começo vão no pedaço: até a última que fechou em silêncio, se ela deixa o pedaço
    // com ao menos metade do teto; senão todas.
    private fun cutPoint(current: List<DictationWindow>, maxChunkMs: Long): Int {
        var elapsed = 0L
        var quiet = -1
        current.forEachIndexed { index, window ->
            elapsed += window.durationMs
            if (window.cut in QUIET_CUTS && elapsed * 2 >= maxChunkMs) quiet = index
        }
        return if (quiet >= 0) quiet + 1 else current.size
    }

    private fun concat(parts: List<ByteArray>): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var offset = 0
        parts.forEach { part ->
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    private val QUIET_CUTS = setOf(WindowCut.Pause, WindowCut.Leading)
}
