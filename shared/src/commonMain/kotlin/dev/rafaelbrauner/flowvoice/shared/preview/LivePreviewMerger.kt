package dev.rafaelbrauner.flowvoice.shared.preview

import dev.rafaelbrauner.flowvoice.shared.pipeline.LiveDictationText
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

// Acumulador da prévia ao vivo: saída idêntica a apply(LivePreviewAssembler.assemble) e ao
// apply(LiveDictationText.split) que o pipeline faz hoje, sem refoldar tudo a cada mudança.
//
// Por que não as funções puras direto: assemble refolda TODOS os segmentos a cada parcial
// (O(S²) em tokenize na nuvem) e liveText() reaplica o dicionário sobre o texto inteiro a cada
// parcial (~4/s no motor local). Aqui a dobra é incremental — o maior prefixo com chaves
// estruturais iguais às do processamento anterior continua em cache e só o sufixo refaz — e o
// apply do vocabulário é memoizado por texto de entrada, invalidado quando revision() muda.
//
// Main-confined: os métodos rodam na thread principal (Compose), mesmo padrão do
// DictationPipeline. Estado simples, sem lock: as chamadas nunca concorrem e o custo de
// sincronização ficaria no caminho quente da prévia sem comprar nada.
class LivePreviewMerger(
    private val applyVocabulary: (String) -> String,
    private val revision: () -> Long
) {
    private val previewMerge = IncrementalMerge()
    private val liveStableMerge = IncrementalMerge()
    private val liveFullMerge = IncrementalMerge()
    private val vocabularyMemo = ApplyMemo()
    private var memoRevision = revision()

    // Via da nuvem: o merge cru vem do cache incremental e o empacotamento (finalizado vs
    // provisório com "…") é recalculado a cada chamada. O memo do merge sobrevive ao flip de
    // sessionComplete porque o merge não muda com o empacotamento. O parcial do motor é juntado
    // pelo pipeline DEPOIS daqui, então não passa por este memo.
    fun preview(segments: List<TranscriptionSegment>, sessionComplete: Boolean): LivePreview {
        val merged = previewMerge.merge(previewPieces(segments))
        val transcribing = segments.any { it.status == TranscriptionSegment.Status.Transcribing }
        return if (sessionComplete && !transcribing) {
            LivePreview(applyWithMemo(merged), applyWithMemo(""))
        } else {
            val suffix = if (transcribing) "…" else ""
            LivePreview(
                finalized = applyWithMemo(""),
                provisional = applyWithMemo(listOf(merged, suffix).filter { it.isNotBlank() }.joinToString(" "))
            )
        }
    }

    // Via do motor no aparelho: replica LiveDictationText.split. O stable é a dobra sem a última
    // janela — não muda entre ticks de parcial, então o memo o mantém; o tail vem da dobra
    // completa e o parcial entra nele antes do apply, igual ao withPartial de hoje, para a saída
    // continuar a mesma do liveText() atual. O tail com parcial é curto e muda a cada tick, então
    // o apply dele não tem memo: 1 chamada por tick, e o stable não paga nada.
    fun live(segments: List<TranscriptionSegment>, partial: String): LivePreview {
        val pieces = livePieces(segments)
        val stable = liveStableMerge.merge(if (pieces.isEmpty()) emptyList() else pieces.subList(0, pieces.size - 1))
        val merged = liveFullMerge.merge(pieces)
        val provisional = merged.removePrefix(stable).trim()
        val transcribing = segments.any { it.status == TranscriptionSegment.Status.Transcribing }
        val tail = listOf(provisional, if (transcribing) LiveDictationText.PENDING_MARK else "")
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return LivePreview(
            finalized = applyWithMemo(stable),
            provisional = applyVocabulary(withPartial(tail, partial))
        )
    }

    // Início de sessão: nada do ditado anterior pode sobrar nos caches.
    fun reset() {
        previewMerge.clear()
        liveStableMerge.clear()
        liveFullMerge.clear()
        vocabularyMemo.clear()
        memoRevision = revision()
    }

    private fun applyWithMemo(text: String): String {
        val current = revision()
        if (current != memoRevision) {
            vocabularyMemo.clear()
            memoRevision = current
        }
        return vocabularyMemo.get(text) ?: applyVocabulary(text).also { vocabularyMemo.put(text, it) }
    }

    private fun withPartial(provisional: String, partial: String): String =
        listOf(provisional, partial).filter { it.isNotBlank() }.joinToString(" ")

    // Nuvem (assemble): janelas Ok com texto ou só '\n'; o recorte que entra na emenda tira só
    // espaço, igual ao assemble.
    private fun previewPieces(segments: List<TranscriptionSegment>) = segments
        .sortedBy { it.windowIndex }
        .filter { it.status == TranscriptionSegment.Status.Ok && (it.text.isNotBlank() || '\n' in it.text) }
        .map { MergePiece(it.windowIndex, it.text.trim(' '), it.contextDurationMs, it.continuous, it.glued) }

    // Motor no aparelho (split): janelas Ok com texto; o recorte tira todo espaço, igual ao split.
    private fun livePieces(segments: List<TranscriptionSegment>) = segments
        .sortedBy { it.windowIndex }
        .filter { it.status == TranscriptionSegment.Status.Ok && it.text.isNotBlank() }
        .map { MergePiece(it.windowIndex, it.text.trim(), it.contextDurationMs, it.continuous, it.glued) }
}

// Chave estrutural de um pedaço na dobra: tudo o que a emenda enxerga. O texto é o recorte que
// entra na emenda (trim do segmento), então mudança só de espaço nas pontas não refaz nada — não
// muda a emenda. Mudou qualquer campo, a dobra refaz dali para o fim.
private data class MergePiece(
    val windowIndex: Int,
    val text: String,
    val contextDurationMs: Long,
    val continuous: Boolean,
    val glued: Boolean
)

// Dobra incremental da emenda: para cada pedaço processado guarda o texto acumulado depois dele.
// A cada chamada acha o maior prefixo cujas chaves continuam idênticas às do processamento
// anterior e refaz a dobra só do sufixo — acumulado[i] depende só dos pedaços 0..i, então o
// prefixo casado garante que o texto acumulado de entrada do sufixo é o mesmo de uma dobra
// completa. Refoldar tudo era o que tornava a prévia O(S²) em tokenize.
private class IncrementalMerge {
    private val pieces = ArrayList<MergePiece>()
    private val accumulated = ArrayList<String>()

    fun merge(next: List<MergePiece>): String {
        var prefix = 0
        val shared = minOf(next.size, pieces.size)
        while (prefix < shared && pieces[prefix] == next[prefix]) prefix++
        if (prefix == next.size && prefix == pieces.size) {
            return if (accumulated.isEmpty()) "" else accumulated.last()
        }
        if (prefix < pieces.size) {
            pieces.subList(prefix, pieces.size).clear()
            accumulated.subList(prefix, accumulated.size).clear()
        }
        var text = if (prefix == 0) "" else accumulated[prefix - 1]
        for (i in prefix until next.size) {
            val piece = next[i]
            text = LivePreviewAssembler.mergeAdjacent(text, piece.text, piece.contextDurationMs, piece.continuous, piece.glued)
            pieces.add(piece)
            accumulated.add(text)
        }
        return text
    }

    fun clear() {
        pieces.clear()
        accumulated.clear()
    }
}

// Memo do apply do vocabulário por texto de entrada. Por chamada da prévia só dois textos são
// procurados (finalized e provisional); o limite pequeno com descarte do menos recente impede a
// memória de crescer com a sessão. A invalidação por revision() fica no LivePreviewMerger.
private class ApplyMemo(limit: Int = 4) {
    private val maxEntries = limit
    private val entries = ArrayList<Pair<String, String>>(limit)

    fun get(text: String): String? {
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.first == text) {
                entries.removeAt(i)
                entries.add(entry)
                return entry.second
            }
        }
        return null
    }

    fun put(text: String, value: String) {
        entries.add(text to value)
        if (entries.size > maxEntries) entries.removeAt(0)
    }

    fun clear() = entries.clear()
}