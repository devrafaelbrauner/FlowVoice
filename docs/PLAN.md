# Plano de Implementação — FlowVoice

Plano técnico de referência para a geração de código (via endpoint Cerebras) e
para a verificação em aparelho real. Este documento é a **fonte de verdade** do
roadmap. Atualizá-lo conforme o trabalho evolui.

## Objetivo

App de ditado **voz → texto** em nuvem (**OpenRouter**), com:

- **Texto durante a fala** (prévia ao vivo).
- **Inserção direta no campo ativo**, **mantendo o teclado atual**
  (`IntelligentKeyboard`).
- **Android primeiro** (dispositivo-padrão: Galaxy S26 Ultra), **pt-BR**,
  **uso pessoal**, preparado para **Windows**.

## Decisões técnicas principais

1. **Inserção de texto** (com teclado atual mantido):
   - Serviço de **Acessibilidade** com `AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR`.
   - **Rota principal** (decidida no F02, Galaxy S26 Ultra):
     `AccessibilityService.getInputMethod()` → `InputMethod.getCurrentInputConnection()`
     → `InputMethod.AccessibilityInputConnection.commitText(...)`.
   - **Fallback secundário**: `ACTION_SET_TEXT` no nó editável focado quando
     `currentInputConnection` for nulo ou em Android < 13 (API 33, onde
     `getInputMethod()` foi introduzido).
   - **Área de transferência é opcional**, não obrigatória.
   - **Inserção incremental (0.5.0, P139)**: por padrão, a bolha digita cada
     janela transcrita assim que fica pronta, sem revisão por IA, só no app e no
     campo de origem (`DirectInsertionGuard`). "Revisar antes de inserir" mantém
     a inserção única no fim. Detalhes em
     [`docs/tasks/P138-P139.md`](tasks/P138-P139.md).
     A pontuação de janela e o default da revisão (ainda desligado) estão no
     roteiro [`docs/tasks/H1-pontuacao-pausas.md`](tasks/H1-pontuacao-pausas.md).
2. **Texto durante a fala** (limitação real):
   - A documentação da OpenRouter **não** comprova entrada contínua de
     microfone com transcrição parcial (streaming de áudio).
   - **Abordagem adotada**: **captura contínua de áudio** + **transcrição
     incremental em janelas de áudio** (chunks).
   - A prévia é **atualizada por revisão** (não por concatenação cega) para
     preservar fluência e evitar duplicação.
3. **Stack**:
   - Kotlin Multiplatform + Compose Multiplatform / Jetpack Compose.
   - **Ktor Client** (requisição OpenRouter), **Kotlin Serialization**.
   - **SQLDelight / SQLite** para estado local (offline-first).
   - **GitHub Actions** (CI).
   - **Windows**: Compose Desktop + adaptadores Win32 (**JNA / `SendInput`**
     quando adequado).
4. **Login / sincronização**:
   - Login **Google obrigatório** (Credential Manager + Google Sign-In).
   - **Supabase Auth + PostgreSQL** (RLS) para sincronizar notas, dicionário
     aprovado e preferências.
   - A **chave OpenRouter fica local e cifrada**; **nunca** sincronizada.
5. **Política de captura / janelas / silêncio** (F03):
   - Captura: `16 kHz`, `PCM 16-bit mono`, em frames de `100 ms`
     (`CaptureFrames`, P140).
   - Janela cortada na pausa natural da fala (`SpeechEndpointing`, P140/P143):
     sai com `≥ 2 s` acumulados, `≥ 400 ms` de fala e `450 ms` de pausa, cortada
     no meio da pausa, com limiares relativos ao piso de ruído da sessão. Sem
     pausa, vale o teto de `DEFAULT_TARGET_DURATION_MS = 4_000L` + `300 ms`, com
     o corte no trecho mais silencioso (P131). A última janela pode ser parcial.
   - Teto de `90` pedidos por sessão, contado na submissão (P107, P140).
   - **Sobreposição de contexto em áudio** (`SPEECH_CONTEXT_MS = 1_000L`, P143):
     cada janela é enviada com o último `1 s` da anterior à frente, porque a API
     de transcrição da OpenRouter não aceita `prompt`. O contexto viaja em
     `DictationWindow.contextPcm`, fora do `pcm`: linha do tempo, contagem de
     bytes e silêncio digital (P124) só olham o áudio próprio da janela. A
     repetição sai do texto em `TranscriptOverlap`.
   - Silêncio: `RMS < 300f` por `800 ms`; finalização automática
     (`autoFinalizeOnSilence`) vem **desativada** por padrão em
     `DictationSessionController`.
   - Detalhes em [`docs/tasks/F03.md`](tasks/F03.md).

## Modelo de dados / estado

- Prefira **offline-first**: estado local é a fonte de verdade; sincronização é
  reconciliação em background, **fora do caminho crítico** do ditado.
- Entidades mínimas: `Note`, `DictionaryTerm (approved)`, `PreferenceSetting`,
  `LocalSession` (janelas de áudio, texto provisório/final), `DiagnosticReport`.

## Roadmap por fases

> Cada fase tem critérios de aceite próprio. Commits pequenos e atômicos por
> unidade. Detalhes das tarefas de F00 em `docs/tasks/F00.md`.

- **F00 — Governança e preparação do repositório**
  - Clonar repo, confirmar `main`, caminho local (`projects/FlowVoice`).
  - Criar: `docs/PLAN.md`, `docs/requirements.md`, `AGENTS.md`,
    `ATTRIBUTIONS.md`, `THIRD_PARTY_NOTICES.md`, `docs/references.md`,
    `docs/tasks/`.
- **F01 — Scaffold Kotlin Multiplatform + Gradle**
  - Estrutura `shared/` (commonMain), `androidApp/`; o `desktopApp/` veio no F13.
  - Ktor, SQLDelight, Kotlin Serialization, DI.
  - CI base (GitHub Actions): compilação + testes Android em PR.
- **F02 — POC de inserção direta (aparelho real)**
  - Serviço de Acessibilidade + `FLAG_INPUT_METHOD_EDITOR` +
    `AccessibilityInputConnection.commitText(...)`.
  - Fallback `ACTION_SET_TEXT` e diagnóstico do porquê de cada escolha.
  - **Validar no Galaxy S26 Ultra** nos apps-alvo.
  - **Decisão de rota:** `commitText` como rota principal e
    `ACTION_SET_TEXT` como fallback.
  - **Validado em app alvo real:** Google Messages
    (`com.google.android.apps.messaging`) no aparelho real `RXGL10CHB5E`;
    detalhes em [`docs/tasks/F02.md`](tasks/F02.md).
- **F03 — Captura de áudio contínua + sessão de ditado**
  - Captura contínua, buffer por janela de áudio, detecção de fim de fala,
    cancelamento, indicador de status.
- **F04 — Integração OpenRouter (transcrição incremental)**
  - `POST /api/v1/audio/transcriptions` com janelas de áudio.
  - Campo de chave OpenRouter + validação; armazenamento local cifrado;
    tratamento de erros/timeout/retry/limite.
- **F05 — Benchmark de modelos de transcrição (aparelho real)**
  - Rodar a **shortlist** (abaixo) com corpus pt-BR; medir **latência**,
    **precisão** e **custo**; escolher modelo padrão + fallback.
  - Requer **limite de gasto** definido pelo usuário.
- **F06 — Texto ao vivo (prévia)**
  - Atualização da prévia por **revisão incremental**; distintivo
    **provisório vs finalizado**; inserção final no campo alvo.
- **F07 — Dicionário pessoal + aprovações**
  - Sugestões de termos, aprovação do usuário, aplicação na revisão.
- **F08 — Notas**
  - Criar/editar/listar notas; persistência local + sync.
- **F09 — Pontuação e ortografia**
  - Revisão/formatação por IA (modelos de revisão, abaixo); toggle on/off.
- **F10 — Login Google obrigatório**
  - Google Sign-In (Credential Manager); proteção de rotas; RLS no backend.
- **F11 — Sincronização**
  - Reconciliação offline-first de notas, dicionário aprovado e preferências.
- **F12 — UX do app principal**
  - Botão flutuante, tela de configuração, diagnóstico técnico exportável,
    onboard.
- **F13 — Preparação para Windows**
  - Compose Desktop + JNA/`SendInput` + DPAPI no módulo `desktopApp`;
    implementado, falta validar num Windows real (F13.7).
- **F14 — Higiene de licença e entrega**
  - `ATTRIBUTIONS.md`, `THIRD_PARTY_NOTICES.md` finais; `LICENSE` (MIT);
    documentação de release; verificação de manutenção.

## Modelos de transcrição (shortlist de benchmark — F05)

**Escolha atual (Galaxy S26 Ultra `RXGL10CHB5E`, 2026-09-15, achado P136):**

- **Padrão:** `openai/gpt-transcribe` — WER 0 nas duas rodadas (28 palavras no total).
  Em comparação, `gpt-4o-mini-transcribe` errou 1 palavra na rodada de 15/set e, ditando pela
  bolha, trocou "ditado" em 2 de 3 frases curtas ("Primeiro digitando", "Terceirizado").
  Custo ~US$ 0,0011 por clipe de ~15 s (~3× o mini); latência de 1420 ms em 15/set e 2151 ms em 13/set.
- **Fallback:** `deepgram/nova-3` — WER 0,06 e 1511 ms em 15/set; WER 0,18 em 13/set.
- Evidência ainda fraca: dois clipes curtos, e o benchmark transcreve o clipe inteiro, não as
  janelas de ~4 s do ditado. Repetir com mais frases reais.
- **Rodada externa complementar (2026-09-17, curtos 11/set, 150 itens, 5,9 min,
  limite US$ 1,00 aprovado, gasto ~US$ 0,12):** `mai-transcribe-2` 19,3% >
  `gpt-transcribe` 19,8% > `turbo` 23,0% > `voxtral` 28,8% >
  `parakeet`/`gpt-4o-transcribe` 35,4% > `gpt-4o-mini` 42,4% > `nova-3` 52,3%.
  Converge no essencial (mai-2 entre os melhores, turbo o mais barato 10×) e
  discrimina melhor que o clipe único. Detalhe em
  [`docs/benchmarks/F05-rodada-externa-curtos.md`](benchmarks/F05-rodada-externa-curtos.md).

**Rodada de 2026-09-15** (clipe de 4 janelas, *Terceiro ditado pela bolha. Paciente refere dor no
joelho direito, sem febre e sem alergia a dipirona.*, 17 palavras; teto US$ 1,00; gasto
US$ 0,0047; 8/8 ok):

| # | Modelo | WER | Latência | Custo USD |
| --- | --- | --- | --- | --- |
| 1 | `openai/gpt-transcribe` | 0,00 | 1420 ms | 0,001125 |
| 2 | `deepgram/nova-3` | 0,06 | 1511 ms | 0,001064 |
| 3 | `openai/gpt-4o-mini-transcribe` | 0,06 | 2346 ms | 0,000345 |
| 4 | `microsoft/mai-transcribe-2` | 0,12 | 1010 ms | 0,000417 |
| 5 | `openai/gpt-4o-transcribe` | 0,12 | 1434 ms | 0,000660 |
| 6 | `mistralai/voxtral-mini-transcribe` | 0,12 | 1831 ms | 0,000700 |
| 7 | `openai/whisper-large-v3-turbo` | 0,12 | 4036 ms | 0,000049 |
| 8 | `nvidia/parakeet-tdt-0.6b-v3` | 0,18 | 1524 ms | 0,000371 |

**Rodada de 2026-09-13** (escolha anterior: padrão `openai/gpt-4o-mini-transcribe`, WER 0, 1321 ms,
~US$ 0,00019; fallback `deepgram/nova-3`). Clipe de 3 janelas, frase *O médico pediu o exame de
sangue para amanhã de manhã.*; teto US$ 1,00; gasto real US$ 0,003; 8/8 modelos ok.

**Rodada 1 (ranking WER → latência → custo):**

| # | Modelo | WER | Latência | Custo USD |
| --- | --- | --- | --- | --- |
| 1 | `openai/gpt-4o-mini-transcribe` | 0,00 | 1321 ms | 0,000191 |
| 2 | `nvidia/parakeet-tdt-0.6b-v3` | 0,00 | 1632 ms | 0,000243 |
| 3 | `openai/gpt-transcribe` | 0,00 | 2151 ms | 0,000750 |
| 4 | `microsoft/mai-transcribe-2` | 0,00 | 2343 ms | 0,000278 |
| 5 | `mistralai/voxtral-mini-transcribe` | 0,00 | 2757 ms | 0,000450 |
| 6 | `openai/gpt-4o-transcribe` | 0,09 | 2756 ms | 0,000373 |
| 7 | `deepgram/nova-3` | 0,18 | 1545 ms | 0,000697 |
| 8 | `openai/whisper-large-v3-turbo` | 0,27 | 2668 ms | 0,000032 |

**Rodada 2 (avaliar conforme resultado):**

- `nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b`
- `qwen/qwen3-asr-0.6b`
- `qwen/qwen3-asr-1.7b`
- `google/chirp-3`

**Modelos de revisão / formatação (F09):**

- `google/gemini-3.1-flash-lite`
- `openai/gpt-4.1-mini`
- `google/gemini-3.8-flash`
- `openai/gpt-4o-mini`

> Escolha: menor WER, desempate por latência. Turbo permanece na shortlist
> como baseline barato, mas não é o padrão.

## Critérios de aceite (globais)

- Compilação limpa + testes passando em CI.
- Inserção direta funcionando nos apps-alvo no S26 Ultra.
- Prévia ao vivo visível com distinção provisório/final.
- Benchmark documentado com modelo padrão escolhido e justificativa.
- Diagnóstico técnico exportável e acionável.
- Nenhum segredo commitado; chave local e cifrada.

## Riscos e mitigações

- **Inserção por Acessibilidade varia por ROO/One UI** → POC cedo (F02) +
  diagnóstico; fallback `ACTION_SET_TEXT`.
- **OpenRouter sem streaming parcial documentado** → transcrição incremental
  por janela (F04/F06); revisar modelo se UX ficar ruim.
- **Custo do benchmark** → definir limite antes do F05; priorizar baseline.
- **Licença / cópia acidental de GPL/AGPL** → `docs/references.md` como barreira;
  `THIRD_PARTY_NOTICES.md` por dependência.

## Pendências / decisões abertas

- Confirmação da **licença MIT** do FlowVoice.
- Novos **apps-alvo adicionais**, se houver, para regressão extra do F02 no
  Galaxy S26 Ultra.
- Dados de aparelho: Galaxy S26 Ultra `SM_S948B` / `RXGL10CHB5E`; teto F05 US$ 1,00 (gasto US$ 0,003).
- Modelo padrão: `openai/gpt-transcribe` (desde 2026-09-15, P136); fallback `deepgram/nova-3`.