package dev.rafaelbrauner.flowvoice.shared.transcription

// Trava curta para estado mexido por quem não suspende (`submit`, `cancel`, `reset`) e pelas
// corrotinas das janelas ao mesmo tempo (P53). No Android tudo roda em `Main.immediate`; no desktop,
// na thread da interface e em `Dispatchers.Default`. Os dois alvos são JVM, e o `synchronized` da
// JVM é reentrante: quem coleta um StateFlow emitido dentro da trava pode voltar ao controller na
// mesma thread sem travar.
internal expect inline fun <T> locked(lock: Any, block: () -> T): T
