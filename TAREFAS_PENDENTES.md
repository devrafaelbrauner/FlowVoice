# Tarefas pendentes

Backlog do que falta para o FlowVoice ficar completo. O detalhamento por fase
continua em [`docs/tasks/`](docs/tasks/README.md); aqui fica a visão priorizada.
Itens concluídos são marcados, não apagados.

**Status:** pendente · em andamento · bloqueado (depende do usuário) · concluída

## Alta

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P01 | Commitar e publicar F04–F12 (um commit por fase, PR #2) | concluída (0.2.0) | checkout local sem commit |
| P02 | `commitText` com `newCursorPosition = 0` deixava o cursor antes do texto | concluída (0.3.0) | /verify |
| P03 | `FlowVoiceAdbReceiver` exportado sem permissão | concluída (0.3.0) | /verify |
| P04 | `allowBackup` restaurava `flowvoice_secrets` sem a chave do Keystore | concluída (0.3.0) | revisão |
| P05 | Ditado pelo botão flutuante sem sair do app-alvo | concluída (0.3.0) | revisão de F12 |
| P06 | Backend de sync real (Supabase Auth + Postgres com RLS) no lugar de `InMemoryRemoteSync` | bloqueado: precisa do projeto Supabase (URL e chave anon) | F11 |
| P07 | Login validado no backend (ID token do Google trocado por sessão Supabase); hoje o `id` do usuário é o e-mail | bloqueado: depende de P06 e do Web Client ID do Google Cloud | F10 |
| P08 | Decidir a licença: `AGENTS.md` propõe MIT, mas os demais projetos do autor são proprietários | bloqueado: decisão do usuário | F00 / F14 |
| P20 | Validar no S26 com fala real: botão flutuante → texto inserido no app-alvo, com dois ditados seguidos na ordem certa | pendente: precisa de alguém falando perto do aparelho | F12.4 |
| P21 | Corrida no finalize: a última janela chegava à fila depois do `awaitIdle` | concluída (0.3.0) | F13 / F12.4 |

## Média

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P09 | `ModelInfo.contextLength` sempre nulo | concluída (0.3.0) | /verify |
| P10 | Rodar `lintDebug` no CI | concluída (0.3.0) | /verify |
| P11 | F13 — preparação para Windows (Compose Desktop, JNA `SendInput`, DPAPI) | concluída (0.3.0), exceto F13.7 | PLAN |
| P12 | F13.7 — validar num Windows real: inserção (emoji, acentos, UIPI), DPAPI, microfone, `packageMsi` | pendente: precisa de um Windows | F13 |
| P13 | F14 — higiene de licença e entrega: `LICENSE`, notices finais, documentação de release | pendente (depende de P08) | PLAN |
| P14 | Persistência em SQLDelight/SQLite, como decidido no PLAN (hoje notas e dicionário são JSON em SharedPreferences) | pendente | PLAN |
| P15 | Evidência em aparelho real de F06 a F11 (os docs dessas fases não registram validação no S26 Ultra) | pendente | docs/tasks |
| P16 | Docs desatualizados (AGENTS, F01, F02, PLAN) | concluída (0.3.0) | /verify |
| P17 | `README.md` com instalação, configuração e build | concluída (0.3.0) | revisão |
| P22 | Desktop: atalho global e janela flutuante que não rouba o foco (hoje minimiza e insere após 3 s) | pendente | F13 |
| P23 | Definir `upgradeUuid` e `menuGroup` do MSI antes do primeiro instalador | pendente | F13 |
| P24 | Onboarding: tratar recusa de `POST_NOTIFICATIONS` (a notificação do serviço de microfone some) | pendente | F12.4 |
| P25 | `TYPE_APPLICATION_OVERLAY` exige API 26 com minSdk 24: proteger por versão ou subir o minSdk | pendente | lint |
| P26 | `stopService` com instância nova em `MainActivity` (`ImplicitSamInstance` no lint) pode não parar o overlay | pendente | lint |

## Baixa

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P18 | Regressão do F02 em apps-alvo adicionais (WhatsApp, Chrome, Obsidian) | pendente | PLAN |
| P19 | Ícone do app (`MissingApplicationIcon` no lint) | pendente | lint |
| P27 | Botão flutuante arrastável, com posição lembrada (hoje cobre a tecla de ação do teclado) | pendente | F12.4 |
| P28 | `GoogleSignInHelper`: tratar `NoCredentialException` e declarar `<queries>` para `resolveActivity` | pendente | lint |
| P29 | Levar dicionário, revisão e notas ao desktop | pendente | F13 |
