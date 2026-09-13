# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e [Versionamento Semântico](https://semver.org/lang/pt-BR/). As fases citadas
estão em [`docs/tasks/`](docs/tasks/README.md).

## [Unreleased]

## [0.3.0] - 2026-09-13

### Added

- F12.4: o botão flutuante dita no app aberto. Um toque grava, outro finaliza e
  insere no campo focado, e um toque longo cancela. A captura roda num foreground
  service de microfone iniciado com a Activity visível.
- `DictationPipeline` no `shared`: sessão, transcrição, prévia, dicionário,
  revisão e inserção, usado pela tela principal e pelo botão flutuante.
- F13 (preparação para Windows): contrato `TextInserter`, captura via
  `javax.sound`, inserção via `SendInput` (JNA), chave cifrada com DPAPI, módulo
  Koin do desktop e app Compose Desktop mínimo (Compose Multiplatform 1.8.2).
- CI roda `lintDebug` e compila o app desktop.
- `README.md` com build, instalação e configuração; `CHANGELOG.md`,
  `TAREFAS_PENDENTES.md` e `MELHORIAS.md`.

### Changed

- Um único contrato `TextInserter` (`shared.insertion`) para Android, pipeline e
  desktop.
- `AGENTS.md`, F01, F02 e PLAN alinhados ao código (compileSdk 35; inserção
  direta exige API 33).
- Versão 0.3.0 no app Android, no `shared` e no desktop.

### Fixed

- `commitText` deixava o cursor antes do texto; trechos seguidos entravam
  invertidos.
- O texto final perdia o último trecho da fala: a última janela chegava à fila
  depois do `awaitIdle` (Android e desktop).
- `ModelInfo.contextLength` vinha sempre nulo (faltava `context_length`).
- O backup restaurava o cofre da chave sem a chave do Keystore; o cofre ilegível
  agora é recriado vazio.

### Security

- O receiver de depuração por adb só existe no build debug e exige
  `android.permission.DUMP`; antes qualquer app podia mandar o FlowVoice digitar
  no campo focado.

### Removed

- Guarda de API morta no fallback `ACTION_SET_TEXT`.

## [0.2.0] - 2026-09-13

Fases F03 a F12 (PR #2, branch `f03-audio-capture-session`).

### Added

- F03: captura contínua de áudio (16 kHz, PCM 16-bit mono), sessão de ditado
  com janelas de ~4 s e detecção de silêncio.
- F04: transcrição incremental via OpenRouter (`/audio/transcriptions`), chave
  guardada cifrada, validação da chave, retry com backoff, `Retry-After` e teto
  de requisições por sessão.
- F05: benchmark de modelos com WER, latência e custo sob teto de gasto.
  Padrão `openai/gpt-4o-mini-transcribe`, fallback `deepgram/nova-3`.
- F06: prévia ao vivo com trechos provisórios e finalizados; inserção única no
  finalize.
- F07: dicionário pessoal com sugestões, aprovação e aplicação na prévia.
- F08: notas locais (criar, editar, apagar).
- F09: revisão opcional de pontuação e ortografia via OpenRouter.
- F10: login Google via Credential Manager.
- F11: `SyncEngine` offline-first para notas, dicionário e preferências (remoto
  ainda em memória).
- F12: botão flutuante, diagnóstico exportável e integração das fases no app.

### Known issues

- Login sem validação do ID token em backend; sync sem servidor real.
- O botão flutuante abre o app e tira o foco do campo do app-alvo (corrigido na
  0.3.0).

## [0.1.0] - 2026-09-13

Fases F00 a F02.

### Added

- F00: plano, requisitos, `AGENTS.md`, atribuições e referências.
- F01: scaffold Kotlin Multiplatform (Android + desktop) com Ktor, Koin e CI.
- F02: POC de inserção direta no campo focado via serviço de acessibilidade
  (`commitText`, fallback `ACTION_SET_TEXT`), validada no Galaxy S26 Ultra.
