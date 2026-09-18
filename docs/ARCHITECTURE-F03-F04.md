# Arquitetura F03/F04 — VAD + StreamRouter + Transcrição incremental

Decisão de arquitetura para `docs/tasks/F03.md` e `docs/tasks/F04.md`.
Somente contratos e divisão de módulos — implementação em fase própria.
Inspiração Handy MIT (`cjpais/handy`): padrões portados, nenhum código copiado.

## 1. Divisão KMP

- `shared/commonMain`: contratos puros, sem import Android.
  - `audio/VoiceActivityDetector.kt`, `audio/VadConfig.kt`,
    `audio/SmoothedVad.kt` (energia/RMS), `audio/VadFrames.kt`
  - `session/StreamRouter.kt`, `session/StreamCmd.kt`,
    `session/DictationSession.kt`, `session/SessionState.kt`,
    `session/StreamText.kt`
  - `api/TranscriptionApi.kt`, `model/TranscriptionModels.kt`,
    `language/EffectiveLanguage.kt`, `glossary/GlossaryBudget.kt`,
    `text/PostProcess.kt`
  - `transcription/TranscriptionManager.kt` (orquestra worker; engine real
    chega em F05/F06, aqui delega ao `TranscriptionApi`)
- `shared/androidMain`: `audio/AndroidAudioCapture.kt` (AudioRecord),
  `audio/SileroVad.kt` (stub energia primeiro, ONNX depois, mesma interface).
- `androidApp`: `service/DictationService.kt` (foreground + notificação),
  `ui/DictationScreen.kt` (estado + provisório/final), DI `androidModule`.
- DI: `sharedModule` (existente) + `audioModule`/`sessionModule` em common +
  `androidModule` (capture real, chave cifrada via `EncryptedSharedPreferences`).

## 2. Contratos Kotlin

```kotlin
// --- VAD ---
enum class VadFrameKind { SPEECH, NOISE }
data class VadFrame(val kind: VadFrameKind, val data: FloatArray? = null)
data class VadTailReport(
    val withheldFrames: Int, val withheldVoicedFrames: Int,
    val inSpeech: Boolean, val onsetCounter: Int, val hangoverCounter: Int
)
data class VadConfig(
    val sampleRateHz: Int = 16000, val frameMs: Int = 30,
    val frameSamples: Int = 480, val prefillMs: Long = 450,
    val offlineHangoverMs: Long = 450, val streamingHangoverMs: Long = 1650,
    val onsetMs: Long = 60, val threshold: Float = 0.5f,
    val extraRecordingBufferMs: Long = 0L, val vadEnabled: Boolean = true
)
interface VoiceActivityDetector {
    val frameSamples: Int
    fun pushFrame(frame: FloatArray): VadFrame
    fun setHangoverFrames(n: Int)
    fun tailReport(): VadTailReport?
    fun reset()
}
fun framesForDurationMs(ms: Long, frameSamples: Int, sampleRateHz: Int = 16000): Int =
    ((ms * sampleRateHz + frameSamples * 1000 - 1) / (frameSamples * 1000)).toInt()

// --- Sessão / StreamRouter ---
sealed interface StreamCmd {
    data class Feed(val pcm: FloatArray) : StreamCmd
    data class Finalize(val reply: CompletableDeferred<FinalizedStreamText?>) : StreamCmd
    data object Cancel : StreamCmd
}
data class FinalizedStreamText(
    val text: String, val outputLanguage: OutputLanguage, val modelId: String
)
enum class SessionState { IDLE, LISTENING, WORKING }
enum class StreamPhase { LISTENING, WORKING }
enum class StreamWorkKind { TRANSCRIBING, POLISHING }
data class StreamText(val committed: String, val tentative: String)

interface StreamRouter {
    val isOpen: Boolean
    fun open()
    fun take(): Channel<StreamCmd>?
    fun clear()
    fun feed(frame: FloatArray)
}
interface DictationSession {
    val state: StateFlow<SessionState>
    val liveText: StateFlow<StreamText>
    suspend fun start()
    suspend fun stop(): FinalizedStreamText?
    suspend fun cancel()
}

// --- Transcrição ---
data class TranscriptionRequest(
    val audio: ByteArray, val model: String, val language: String = "pt",
    val prompt: String? = null, val temperature: Float? = null,
    val translateToEnglish: Boolean = false
)
data class TranscriptionResult(
    val text: String, val outputLanguage: OutputLanguage,
    val modelId: String, val latencyMs: Long
)
enum class OutputLanguage { PT, EN, AUTO, UNKNOWN }
interface TranscriptionApi {
    suspend fun transcribe(request: TranscriptionRequest, apiKey: String): TranscriptionResult
}
fun effectiveLanguage(intent: String, supported: List<String>, supportsDetection: Boolean): String
class GlossaryBudget(val maxTokens: Int = 224, val bytesPerTokenPtBr: Double = 2.51) {
    fun budget(terms: List<String>): Pair<String?, GlossaryReport>
}
data class GlossaryReport(val promptTokens: Int, val droppedTerms: Int)
fun normalizeTranscriptionOutput(text: String): String
fun removeFillerWords(text: String, custom: List<String>? = null, enabled: Boolean = true): String
fun applyCustomWords(text: String, words: List<String>, threshold: Double = 0.18): String
```

## 3. Fluxos

- Captura → VAD → janela 2,5 s overlap 0,5 s → `StreamRouter.Feed`.
  Callback consulta `isOpen` (`AtomicBoolean` single load) antes de enfileirar.
- `stop()` → `Finalize` FIFO (frames antes do finalize nunca perdidos) →
  worker reconcilia overlap (revisão, `committed` só cresce) → `TranscriptionApi`
  por janela → texto final → `commitText` (F02) no campo ativo.
- `Finalize` sem stream → `null` → fallback batch da janela acumulada.
- `cancel()` → `Cancel`, sem rede, sem texto, engine devolvida.
- Erros: 401 chave inválida, 402 sem créditos, 404 modelo, 429 retry backoff,
  5xx 1 retry, timeout 60 s/janela; nada loga `Authorization`/áudio.
- Chave: tela config + validar via `listTranscriptionModels` existente;
  `EncryptedSharedPreferences` AES256_GCM; F11 exclui explicitamente do sync.

## 4. Concorrência (espelho Handy, primitivas Kotlin)

- `Channel<StreamCmd>(UNLIMITED)` garante FIFO Feed→Finalize.
- Worker único: `AtomicLong workerId` + `compareAndSet`; `Mutex` protege engine;
  `CompletableDeferred` substitui `Condvar` do `LoadingGuard`.
- `touchActivity()` por janela; unload default Min5, nunca `Immediately` no ditado.
- `returnEngine`: devolve se `currentModelId` inalterado, senão descarta stale.
- `SmoothedVad.reset()` por sessão (limpa onset/hangover, equivalente ao reset
  LSTM do Silero).

## 5. Dependências

- Adicionar na implementação (versão fixada em `libs.versions.toml`):
  `kotlinx-coroutines-core` (obrigatória F03.4/F04.6),
  `androidx.security:security-crypto` (F04.3, avaliar versão),
  `onnxruntime-android` (somente avaliação F03.2, registrar em
  `THIRD_PARTY_NOTICES.md` antes de usar).
- NÃO adicionar: `ktor-client-logging` (risco vazar chave), WebSocket/SSE
  (OpenRouter sem streaming parcial documentado), modelo local embarcado
  (só benchmark F05 decide).

## 6. Critérios de arquitetura

- `commonMain` compila sem SDK Android (`:shared:desktopTest` prova).
- Tudo testável em JVM com fakes (`TranscriptionApi` fake, áudio sintético).
- Pronta para F05 (trocar `model` sem mudar contrato) e F06 (prévia consome
  `liveText: StateFlow<StreamText>` sem mudança).
- Diagnóstico: `tailReport`, `promptTokens/droppedTerms`, latência por janela,
  `effectiveLanguage` resolvido — exportável (base F12).

## 7. Riscos e abertas

- Silero ONNX em CPU mid-range pode estourar orçamento; fallback energia sempre
  disponível via mesma interface.
- OpenRouter sem streaming parcial: latência piso = janela + RTT; mitigar com
  janela 2–3 s e upload paralelo à captura.
- Custo F04.7 exige limite de gasto prévio (pendência PLAN); 1 chamada real curta
  primeiro.
- `AudioRecord` + foreground service + `RECORD_AUDIO` rationale pt-BR; OEMs
  (One UI) podem matar serviço — diagnóstico deve expor.
