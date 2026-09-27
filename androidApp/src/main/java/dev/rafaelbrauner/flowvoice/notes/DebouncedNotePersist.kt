package dev.rafaelbrauner.flowvoice.notes

import dev.rafaelbrauner.flowvoice.shared.notes.Note
import dev.rafaelbrauner.flowvoice.shared.notes.NotePersist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Editar uma nota gravava a lista inteira de notas em JSON a cada tecla, na thread principal (Y6). A
// lista em memória continua sendo a verdade para quem lê; a gravação espera o usuário parar de digitar
// por SAVE_DELAY_MS e roda no `scope` (fora da thread principal). `flush` grava na hora o que estiver
// esperando: a activity chama ao pausar, para nada se perder se o processo morrer em segundo plano.
class DebouncedNotePersist(
    private val delegate: NotePersist,
    private val scope: CoroutineScope,
    private val delayMs: Long = SAVE_DELAY_MS
) : NotePersist {
    private val pendingLock = Any()
    private val writeLock = Any()
    private var pending: List<Note>? = null
    private var scheduled: Job? = null

    override fun load(): List<Note> = delegate.load()

    override fun save(notes: List<Note>) {
        synchronized(pendingLock) {
            pending = notes
            scheduled?.cancel()
            scheduled = scope.launch {
                delay(delayMs)
                flush()
            }
        }
    }

    // Tirar o pendente e gravá-lo sob a mesma trava mantém a ordem: quem tira depois grava depois, e
    // uma gravação atrasada nunca sobrescreve uma lista mais nova.
    fun flush() {
        synchronized(writeLock) {
            val snapshot = synchronized(pendingLock) { pending.also { pending = null } } ?: return
            delegate.save(snapshot)
        }
    }

    companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
