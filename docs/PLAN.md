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
     `currentInputConnection` for nulo ou em Android < 11.
   - **Área de transferência é opcional**, não obrigatória.
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
   - Captura: `16 kHz`, `PCM 16-bit mono`.
   - Janela alvo fixa: `DEFAULT_TARGET_DURATION_MS = 4_000L` (`~4 s`); a última
     janela pode ser parcial.
   - **Sem sobreposição de contexto** nesta versão.
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
  - Estrutura `shared/` (commonMain), `androidApp/`, (futuro) `desktopApp/`.
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
  - Compose Desktop + JNA/`SendInput`; contratos prontos, implementação
    adiada.
- **F14 — Higiene de licença e entrega**
  - `ATTRIBUTIONS.md`, `THIRD_PARTY_NOTICES.md` finais; `LICENSE` (MIT);
    documentação de release; verificação de manutenção.

## Modelos de transcrição (shortlist de benchmark — F05)

**Rodada 1 (princpais):**

- `openai/whisper-large-v3-turbo` — **baseline** rápido.
- `openai/gpt-4o-mini-transcribe` — candidato equilibrado.
- `openai/gpt-transcribe` — comparação de maior precisão.
- `openai/gpt-4o-transcribe` — referência adicional.
- `deepgram/nova-3` — forte em pontuação / uso cotidiano.
- `microsoft/mai-transcribe-2` — precisão multilíngue / saída estruturada.
- `nvidia/parakeet-tdt-0.6b-v3` — velocidade + português.
- `mistralai/voxtral-mini-transcribe` — eficiência.

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

> A selection final deve refletir o **trade-off latência × precisão pt-BR ×
> custo** medido no aparelho real, e ser registrada aqui com os números.

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
- Dados de aparelho (Android/One UI, microfone) e **limite de gasto**.
- Escolha final de modelo (após F05).