package dev.rafaelbrauner.flowvoice.shared.localasr

import kotlinx.coroutines.CoroutineDispatcher

// Reconhecedor de fala em streaming no aparelho: recebe o áudio aos poucos e devolve, a qualquer
// momento, os tokens que já reconheceu.
//
// Contrato de thread: todos os métodos bloqueiam e são chamados de uma thread só, a do
// `LocalDictationDriver`, nunca da thread principal.
//
// Um fluxo por ditado: `start` → (`accept` | `tokens` | `finishUtterance`)* → `close`. O fluxo não
// recomeça nos cortes do ditado: no teclado, um fluxo novo por trecho perdia palavras inteiras quando
// o corte caía dentro delas ("Bom dia" perdia 2/3 no S26). Os tokens de um transducer em greedy
// search nunca são retirados depois de emitidos, e é isso que deixa o driver fechar pedaços.
//
// Qualquer método pode lançar: o driver aposenta o motor pelo resto do ditado e guarda o que já foi
// reconhecido.
interface StreamingSpeechEngine {
    // Rótulo curto, só para o log (`engine=<name>`).
    val name: String

    // Abre o fluxo do ditado. Pode demorar (carga do modelo, ~1,7 s no S26); o áudio que chega enquanto
    // isso espera na fila do driver.
    fun start(language: String)

    // Mais áudio: mono, 16 kHz, float em [-1, 1].
    fun accept(samples: FloatArray)

    // Todos os tokens do fluxo desde `start`, na ordem. Cada token traz o próprio espaço à frente quando
    // começa palavra (" B", "om"); juntá-los dá o texto.
    fun tokens(): List<String>

    // Empurra pelo modelo o áudio já recebido, com o silêncio de cauda que ele precisa, para que a
    // última palavra saia em tokens. Só onde o ditado parou de verdade (pausa, fim): silêncio inventado
    // no meio da fala partiria a palavra.
    fun finishUtterance()

    // Solta o que o ditado segura. Idempotente.
    fun close()
}

// Quem cria o motor do aparelho. Null no desktop (só nuvem).
interface LocalSpeechEngines {
    // O modelo está inteiro no aparelho, com os tamanhos exatos.
    fun modelInstalled(): Boolean

    fun create(): StreamingSpeechEngine

    // A thread única do motor, a mesma para todos os ditados: dois ditados seguidos (o que termina e
    // o que começa) não disputam os núcleos, e o `close` de um vem antes do `start` do seguinte.
    val dispatcher: CoroutineDispatcher
}
