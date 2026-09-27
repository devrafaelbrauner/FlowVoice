package dev.rafaelbrauner.flowvoice.shared.dictation

// Cauda do ditado (Y3): depois do `stop()`, o buffer do sistema (AudioRecord, TargetDataLine) ainda
// guarda o áudio que a leitura não chegou a pegar — o fim da última palavra, quando o toque de parar
// vem logo depois dela. Esta é a decisão pura de esvaziá-lo: ler até vir vazio, sem nunca passar do
// tamanho do buffer, e parar assim que quem para a captura deixa de esperar pela cauda.
object CaptureTail {
    // `read` não pode bloquear: devolve o que há no buffer, zero quando acabou, negativo em erro.
    // Uma leitura vazia logo no começo pode ser só o destravar do `stop()` ainda pendente na
    // plataforma; a segunda vazia encerra. Devolve quantos bytes foram entregues.
    fun drain(maxBytes: Int, read: () -> Int, deliver: (Int) -> Unit, waiting: () -> Boolean): Int {
        var drained = 0
        var unblockPending = true
        while (drained < maxBytes && waiting()) {
            val bytes = read()
            if (bytes < 0) break
            if (bytes == 0) {
                if (drained > 0 || !unblockPending) break
                unblockPending = false
                continue
            }
            deliver(bytes)
            drained += bytes
        }
        return drained
    }
}
