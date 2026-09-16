package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.math.max
import kotlin.math.min

internal data class BlockCut(val block: Int, val cut: WindowCut)

// Decisão do corte na pausa natural da fala (P140), sobre os níveis dos blocos de 20 ms do áudio
// acumulado. Código puro: o agregador mede os blocos e corta os bytes.
internal class SpeechEndpointer(private val config: SpeechEndpointing) {
    private val minPauseBlocks = blocksFor(config.minPauseMs)
    private val minVoicedBlocks = blocksFor(config.minVoicedMs)
    private val minVoicedRunBlocks = blocksFor(config.minVoicedRunMs)
    private val minBufferedBlocks = blocksFor(config.minBufferedMs)
    private val leadingPadBlocks = blocksFor(config.leadingPadMs)
    private val noise = NoiseFloorEstimator(
        capacity = blocksFor(config.noiseHistoryMs),
        percentile = config.noisePercentile,
        contrastPercentile = config.contrastPercentile,
        contrastFactor = config.speechFactor
    )

    val noiseFloor: Int
        get() = noise.estimate()

    fun record(level: Int) = noise.add(level)

    fun clear() = noise.clear()

    // `ceilingSearch` só vem quando o áudio acumulado chegou ao teto: é a faixa do trecho mais
    // silencioso, usada quando não há pausa real.
    fun next(levels: List<Int>, bufferedMs: Long, ceilingSearch: IntRange?): BlockCut? {
        val scan = scan(levels)
        if (bufferedMs >= config.minBufferedMs) {
            // Pausa real depois de fala bastante: corta no meio da pausa mais recente.
            scan.quietRuns
                .lastOrNull { it.size >= minPauseBlocks && scan.voicedBefore(it.first) >= minVoicedBlocks }
                ?.let { return BlockCut(it.first + it.size / 2, WindowCut.Pause) }
        }
        if (ceilingSearch == null) return null
        // Um trecho sem fala antes da fala vai sozinho, cortado um pouco antes dela: cortar no alvo
        // cairia no meio da frase que começou tarde.
        scan.quietRuns
            .lastOrNull { it.size >= minPauseBlocks && scan.voicedBefore(it.first) < minVoicedBlocks }
            ?.let { max(it.first + it.size / 2, it.last + 1 - leadingPadBlocks) }
            ?.takeIf { it >= minBufferedBlocks }
            ?.let { return BlockCut(it, WindowCut.Leading) }
        return BlockCut(quietestCut(levels, ceilingSearch), WindowCut.Ceiling)
    }

    private class Scan(val quietRuns: List<IntRange>, private val voicedPrefix: IntArray) {
        fun voicedBefore(block: Int): Int = voicedPrefix[block]
    }

    // O limiar acompanha o ruído de fundo da sessão: quieto é até `pauseFactor` vezes o piso, e fala
    // é acima de `speechFactor` vezes o piso, cada um com um mínimo absoluto. Entre os dois, o bloco
    // não conta como pausa nem como fala.
    private fun scan(levels: List<Int>): Scan {
        val floor = noise.estimate()
        val pauseLevel = max(config.minPauseLevel, (floor * config.pauseFactor).toInt())
        val quietRuns = runs(levels) { it <= pauseLevel }
        val counted = countedVoiced(levels)
        val voicedPrefix = IntArray(levels.size + 1)
        for (block in levels.indices) voicedPrefix[block + 1] = voicedPrefix[block] + if (counted[block]) 1 else 0
        return Scan(quietRuns, voicedPrefix)
    }

    // Quantos blocos contam como fala, pela mesma regra do corte (P144). Usado para saber se a janela
    // tem fala própria antes de gastar uma requisição com ela.
    fun voicedBlocks(levels: List<Int>): Int = countedVoiced(levels).count { it }

    // Só conta como fala um trecho de ao menos `minVoicedRunMs`: estalo de tecla ou toque na mesa
    // dura um ou dois blocos e não libera o corte nem faz a janela parecer falada.
    private fun countedVoiced(levels: List<Int>): BooleanArray {
        val speechLevel = max(config.minSpeechLevel, (noise.estimate() * config.speechFactor).toInt())
        val counted = BooleanArray(levels.size)
        runs(levels) { it > speechLevel }
            .filter { it.size >= minVoicedRunBlocks }
            .forEach { run -> run.forEach { counted[it] = true } }
        return counted
    }

    private inline fun runs(levels: List<Int>, predicate: (Int) -> Boolean): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (block in levels.indices) {
            if (predicate(levels[block])) {
                if (start < 0) start = block
            } else if (start >= 0) {
                runs += start until block
                start = -1
            }
        }
        if (start >= 0) runs += start until levels.size
        return runs
    }

    // Mesma regra do corte antigo (P131): meio do trecho de 120 ms com menor nível médio na faixa.
    private fun quietestCut(levels: List<Int>, range: IntRange): Int {
        if (range.isEmpty()) return range.first
        val run = min(QUIET_RUN_BLOCKS, range.size)
        var sum = (range.first until range.first + run).sumOf { levels[it].toLong() }
        var best = sum
        var bestStart = range.first
        for (start in range.first + 1..range.last - run + 1) {
            sum += levels[start + run - 1] - levels[start - 1]
            if (sum < best) {
                best = sum
                bestStart = start
            }
        }
        return bestStart + run / 2
    }

    private val IntRange.size: Int
        get() = if (isEmpty()) 0 else last - first + 1

    private companion object {
        const val QUIET_RUN_BLOCKS = 6

        fun blocksFor(durationMs: Long): Int =
            ((durationMs + SpeechEndpointing.BLOCK_MS - 1) / SpeechEndpointing.BLOCK_MS).toInt()
    }
}

// Piso de ruído da sessão: percentil baixo dos níveis dos últimos blocos. Subir exige que o ruído
// novo ocupe quase todo o histórico; descer basta o percentil, então o erro é para o lado de não
// ver pausa (janela no teto, como antes), não de cortar dentro da fala.
// Sem contraste no histórico (percentil alto abaixo de `contrastFactor` vezes o baixo), ele só tem
// ruído constante ou só fala contínua, e o percentil baixo seria o nível da própria fala: o piso
// fica desconhecido (0) e só os mínimos absolutos valem.
internal class NoiseFloorEstimator(
    private val capacity: Int,
    private val percentile: Int,
    private val contrastPercentile: Int,
    private val contrastFactor: Double
) {
    private val levels = IntArray(capacity)
    private var size = 0
    private var next = 0
    private var cached: Int? = null

    fun add(level: Int) {
        levels[next] = level
        next = (next + 1) % capacity
        if (size < capacity) size++
        cached = null
    }

    fun estimate(): Int {
        cached?.let { return it }
        if (size == 0) return 0
        val sorted = levels.copyOf(size).also { it.sort() }
        val low = sorted[(size - 1) * percentile / 100]
        val high = sorted[(size - 1) * contrastPercentile / 100]
        return (if (high >= low * contrastFactor) low else 0).also { cached = it }
    }

    fun clear() {
        size = 0
        next = 0
        cached = null
    }
}
