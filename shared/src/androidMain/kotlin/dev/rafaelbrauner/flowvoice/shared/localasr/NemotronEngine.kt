package dev.rafaelbrauner.flowvoice.shared.localasr

import android.content.ComponentCallbacks2
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionEventLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val SAMPLE_RATE = 16_000

// Silêncio antes do começo do fluxo (0,5 s) e na cauda de cada fechamento (0,8 s), os padrões da
// linha de comando do sherpa-onnx com que o modelo foi medido. O encoder só decodifica blocos inteiros
// de 560 ms: sem silêncio bastante depois da última palavra, o fim dela fica preso no bloco que não
// fechou. Com 0,6 s, no S26 (2026-09-28), o "s" de "minutos" só saiu no pedaço seguinte.
private const val LEADING_PADDING = SAMPLE_RATE / 2
private const val TAIL_PADDING = SAMPLE_RATE * 8 / 10

// Núcleos por decodificação, os da medição no S26 (RTF ≈ 0,14).
private const val THREADS = 4

// A pasta do modelo está inteira: os quatro arquivos, cada um com o tamanho exato. Arquivo faltando
// ou truncado é como não haver modelo — o ditado não começa para falhar ao carregar.
object NemotronModelDir {
    fun isComplete(dir: File): Boolean =
        NemotronModel.FILES.all { File(dir, it.name).let { file -> file.isFile && file.length() == it.bytes } }
}

// O reconhecedor do Nemotron, um por processo. Criá-lo custa ~1,7 s no S26 e perto de 700 MB de
// memória: fica carregado entre um ditado e outro e só é solto depois de IDLE_RELEASE_MS sem ditado
// ou quando o sistema pede memória com urgência. Nunca enquanto um ditado o usa (`acquire`/`release`
// contam quem usa). A liberação roda numa thread própria: o onTrimMemory chega na principal.
object NemotronRecognizer {
    const val IDLE_RELEASE_MS = 5 * 60 * 1000L

    private val lock = Any()
    private var recognizer: OnlineRecognizer? = null
    private var loadedDir: String? = null
    private var users = 0
    private var pendingRelease: ScheduledFuture<*>? = null
    @Volatile
    var eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "nemotron-release").apply { isDaemon = true }
    }

    fun acquire(dir: File): OnlineRecognizer = synchronized(lock) {
        pendingRelease?.cancel(false)
        pendingRelease = null
        val path = dir.absolutePath
        recognizer?.takeIf { loadedDir == path }?.let { current ->
            users++
            eventLog.log("local_engine_load", mapOf("engine" to "nemotron", "loadMs" to "0", "cache" to "true"))
            return current
        }
        check(users == 0) { "reconhecedor em uso com outro modelo" }
        recognizer?.release()
        recognizer = null
        loadedDir = null
        val start = SystemClock.elapsedRealtime()
        val created = OnlineRecognizer(config = config(dir))
        eventLog.log(
            "local_engine_load",
            mapOf(
                "engine" to "nemotron",
                "loadMs" to (SystemClock.elapsedRealtime() - start).toString(),
                "cache" to "false"
            )
        )
        recognizer = created
        loadedDir = path
        users++
        created
    }

    fun release() = synchronized(lock) {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0 && recognizer != null) {
            pendingRelease?.cancel(false)
            pendingRelease = scheduler.schedule({ releaseIfIdle("idle") }, IDLE_RELEASE_MS, TimeUnit.MILLISECONDS)
        }
    }

    // `Application.onTrimMemory`: solta agora, se ninguém usa, nos pedidos urgentes.
    fun onTrimMemory(level: Int) {
        if (!shouldReleaseOnTrim(level)) return
        scheduler.execute { releaseIfIdle("memory_$level") }
    }

    // A pasta do modelo vai ser apagada: o reconhecedor carregado dela não pode continuar na memória.
    fun releaseNowIfIdle() {
        scheduler.execute { releaseIfIdle("model_deleted") }
    }

    private fun releaseIfIdle(reason: String) = synchronized(lock) {
        if (users > 0) return@synchronized
        val current = recognizer ?: return@synchronized
        pendingRelease?.cancel(false)
        pendingRelease = null
        current.release()
        recognizer = null
        loadedDir = null
        eventLog.log("local_engine_released", mapOf("engine" to "nemotron", "reason" to reason))
    }

    private fun config(dir: File) = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80, dither = 0f),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = File(dir, "encoder.int8.onnx").absolutePath,
                decoder = File(dir, "decoder.int8.onnx").absolutePath,
                joiner = File(dir, "joiner.int8.onnx").absolutePath
            ),
            tokens = File(dir, "tokens.txt").absolutePath,
            numThreads = THREADS,
            provider = "cpu"
        ),
        // O fim de cada pedaço é decidido pelo corte do ditado, não pelo modelo.
        enableEndpoint = false,
        // A única que o sherpa-onnx implementa para o transducer NeMo (sem hotwords): outra derruba
        // o processo ao criar o reconhecedor.
        decodingMethod = "greedy_search"
    )
}

// `UI_HIDDEN` e `BACKGROUND` ficam de fora: chegam toda vez que o app some da tela, e soltar ali faria
// cada ditado pagar a carga de novo. Só os níveis em que o sistema está de fato sem memória.
@Suppress("DEPRECATION")
fun shouldReleaseOnTrim(level: Int): Boolean = when (level) {
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> true
    else -> level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
}

// Um fluxo só por ditado sobre o reconhecedor do processo. O idioma vai por fluxo
// (`setOption("language", …)`, lido pelo transducer NeMo do sherpa-onnx 1.13.8).
class NemotronEngine(private val dir: File) : StreamingSpeechEngine {
    override val name: String = "nemotron"

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    override fun start(language: String) {
        val current = recognizer ?: NemotronRecognizer.acquire(dir).also { recognizer = it }
        stream?.release()
        val created = current.createStream()
        created.setOption("language", language)
        created.acceptWaveform(FloatArray(LEADING_PADDING), SAMPLE_RATE)
        stream = created
    }

    override fun accept(samples: FloatArray) {
        val current = stream ?: error("motor sem fluxo")
        current.acceptWaveform(samples, SAMPLE_RATE)
        decodeReady(current)
    }

    override fun tokens(): List<String> {
        val current = recognizer ?: error("motor fechado")
        val open = stream ?: error("motor sem fluxo")
        return current.getResult(open).tokens.map { it.replace(SENTENCEPIECE_SPACE, ' ') }
    }

    override fun finishUtterance() {
        val current = stream ?: error("motor sem fluxo")
        current.acceptWaveform(FloatArray(TAIL_PADDING), SAMPLE_RATE)
        decodeReady(current)
    }

    override fun close() {
        stream?.release()
        stream = null
        if (recognizer != null) {
            recognizer = null
            NemotronRecognizer.release()
        }
    }

    private fun decodeReady(open: OnlineStream) {
        val current = recognizer ?: error("motor fechado")
        while (current.isReady(open)) current.decode(open)
    }

    private companion object {
        const val SENTENCEPIECE_SPACE = '\u2581'
    }
}

class NemotronEngines(private val dir: File) : LocalSpeechEngines {
    override fun modelInstalled(): Boolean = NemotronModelDir.isComplete(dir)

    override fun create(): StreamingSpeechEngine = NemotronEngine(dir)

    override val dispatcher: CoroutineDispatcher
        get() = ENGINE_THREAD

    private companion object {
        // Daemon: nunca segura o processo vivo.
        val ENGINE_THREAD: CoroutineDispatcher by lazy {
            Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "flowvoice-local-asr").apply { isDaemon = true } }
                .asCoroutineDispatcher()
        }
    }
}
