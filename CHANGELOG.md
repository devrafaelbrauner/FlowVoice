# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e [Versionamento Semântico](https://semver.org/lang/pt-BR/). As fases citadas
estão em [`docs/tasks/`](docs/tasks/README.md).

## [Unreleased]

## [0.4.2] - 2026-09-13

Correções dos achados do `/verificar` da 0.4.1 e do teste real no S26.

### Fixed

- P67: ditado de nota não fica mais órfão ao trocar de aba ou girar a tela; um
  coordenador de escopo de app anexa o texto à nota e as Notas voltam a mostrar
  o botão de parar.
- P91: sessão iniciada pelo microfone do Início mostrava só a bolha, gravando
  sem controle; a barra aparece e tocar na bolha durante uma sessão a assume.
- P92: o teto de requisições por sessão encerra a captura e entrega o texto até
  ali, com aviso, em vez de gravar indefinidamente.
- P90: telas do sistema (acessibilidade, sobreposição, detalhes do app,
  compartilhar) abriam na tarefa do FlowVoice, e o ícone reabria Configurações.
- P80: inserção recusada mantém o texto na barra para tentar de novo.
- P83: o destino da inserção é conferido também no ditado iniciado pelo Início.
- P81: o cache de acessibilidade é limpo antes de ler janelas e o campo focado
  (barra acompanha o teclado; fallback não usa texto antigo).
- P69: nomes para o TalkBack em toggles e campos.

### Added

- P66: o onboarding e o README orientam a liberar "configurações restritas" em
  instalações fora de loja (Android 13+) e a não usar o FlowVoice como atalho de
  acessibilidade.
- P84: o CI roda os testes unitários do app Android.

## [0.4.1] - 2026-09-13

Correções dos achados da auditoria de segurança da 0.3.1 (SEG-1 a SEG-7).

### Security

- SEG-6: a chave OpenRouter passa a ser cifrada direto no Android Keystore
  (AES-256-GCM, cofre `flowvoice_vault`), sem o fallback do Tink que gravava o
  keyset em claro; a chave do cofre antigo é migrada na primeira abertura e o
  arquivo antigo só é apagado depois de a chave ser confirmada no cofre novo.
- SEG-1: notas, dicionário, conta e os cofres saem do backup em nuvem, da
  transferência entre aparelhos e do backup completo.
- SEG-7: a rota direta recusa campo de senha, e a inserção é recusada se o app em
  foco mudou desde o início do ditado.
- SEG-5: o serviço de acessibilidade não assina mais nenhum tipo de evento.
- SEG-4: o diagnóstico do campo focado não devolve conteúdo, e o caminho de
  depuração saiu do código principal.
- SEG-3: o log não registra mais o pacote do app em que se dita.
- SEG-2: apagados os APKs debug antigos publicados pelo CI a partir da `main`.

## [0.4.0] - 2026-09-13

Redesign do app a partir do handoff de design (F12 UX).

### Added

- Sistema visual: tema escuro (principal) e claro, Instrument Sans e JetBrains
  Mono empacotadas (SIL OFL 1.1), ícones vetoriais e componentes (pílula de
  status, botão de microfone com pulso, waveform, texto provisório pontilhado,
  toggle, cartões, barra de navegação).
- Telas novas: login, onboarding com as três permissões reais, Início (estado do
  serviço, microfone, últimas notas), Notas em mestre-detalhe com painel
  colapsável e ditado na nota, Dicionário, Ajustes e Diagnóstico (log do
  serviço, exportar relatório e benchmark F05).
- Barra de ditado ao vivo (variação 1b) no botão flutuante: texto finalizado e
  provisório, cronômetro, Cancelar e Inserir, ancorada acima do teclado.
- Pipeline com revisão antes de inserir (`finalizeForReview` → `Ready` →
  `insertReady`), sem mudar o `finalize` usado pelas Notas.

### Changed

- A `MainActivity` virou só hospedeiro da navegação; o microfone do Início abre
  a barra de ditado no app anterior.
- A chave salva só aparece mascarada (`sk-or-v1-••••` + 4 últimos caracteres).
- Login opcional enquanto não há backend (P07).
- O serviço de acessibilidade passa a ler os limites da janela do teclado
  (`flagRetrieveInteractiveWindows`) para posicionar a barra.

### Removed

- Tela POC antiga da `MainActivity`; as funções dela foram para Ajustes e
  Diagnóstico.

## [0.3.1] - 2026-09-13

Correções dos achados da verificação de `9d527d7` (ver `TAREFAS_PENDENTES.md`).

### Fixed

- P30: o fallback `ACTION_SET_TEXT` apagava o conteúdo do campo do app-alvo;
  agora insere no cursor e recusa em campo de senha ou texto ilegível.
- P31: trecho com falha sumia do texto sem aviso; o texto parcial é inserido com
  aviso do trecho e do motivo, e sem nenhum trecho transcrito o motivo real aparece.
- P32: toque duplo ou cancelar durante o início do ditado geravam erro ou eram
  ignorados (novo estado `Starting`).
- P33: ditado pela tela do FlowVoice podia cair nos próprios campos do app (ex.:
  Google Web Client ID) e ser salvo nas preferências.
- P45: o runtime empacotado do desktop não tinha `java.net.http`, e a chave nunca
  validava no app instalado.
- P46: no desktop, iniciar ou cancelar durante a transcrição final apagava ou
  misturava o texto de outra sessão.
- P47 e P41: falha de captura depois do início (desktop e Android) deixava a
  sessão "gravando" sem áudio.
- P36: com a chave-mestra do Keystore inutilizável o app caía em loop ao abrir, e
  uma falha passageira apagava a chave salva; o cofre agora só é recriado em
  corrupção real e, fora isso, fica "indisponível" sem derrubar o app.
- P37: o botão flutuante derrubava o app quando a sobreposição falhava
  (permissão revogada ou Android 7); a permissão é conferida de novo e a falha é
  tratada.

### Changed

- `AudioCaptureEngine` avisa erro durante a captura (`start(onFrame, onError)`,
  compatível com as implementações existentes).
- F12.4 volta para "em andamento" até o aceite com fala real (P34).

### Added

- Testes de regressão do pipeline: sessões seguidas, inserção falha, erro de
  captura e frames de outra thread (P35).
- CI confere que o runtime do desktop inclui `java.net.http` e roda os testes do
  `desktopApp`.

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
