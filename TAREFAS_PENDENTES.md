# Tarefas pendentes

Backlog do que falta para o FlowVoice ficar completo. O detalhamento por fase
continua em [`docs/tasks/`](docs/tasks/README.md); aqui fica a visão priorizada.
Itens concluídos são marcados, não apagados.

**Status:** pendente · em andamento · bloqueado (depende do usuário) · concluída

## Alta

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P01 | Commitar e publicar F04–F12 (um commit por fase, PR #2) | concluída | checkout local sem commit |
| P02 | `commitText` com `newCursorPosition = 0` deixa o cursor antes do texto; trechos seguidos entram invertidos | em andamento | /verify |
| P03 | `FlowVoiceAdbReceiver` exportado sem permissão: qualquer app injeta texto no campo focado em build debug | em andamento | /verify |
| P04 | `allowBackup` restaura `flowvoice_secrets` sem a chave do Keystore | em andamento | revisão |
| P05 | Ditado pelo botão flutuante sem sair do app-alvo (hoje abre a `MainActivity` e perde o foco) | em andamento | revisão de F12 |
| P06 | Backend de sync real (Supabase Auth + Postgres com RLS) no lugar de `InMemoryRemoteSync` | bloqueado: precisa do projeto Supabase (URL e chave anon) | F11 |
| P07 | Login validado no backend (ID token do Google trocado por sessão Supabase); hoje o `id` do usuário é o e-mail | bloqueado: depende de P06 e do Web Client ID do Google Cloud | F10 |
| P08 | Decidir a licença: `AGENTS.md` propõe MIT, mas os demais projetos do autor são proprietários | bloqueado: decisão do usuário | F00 / F14 |

## Média

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P09 | `ModelInfo.contextLength` sempre nulo (falta `@SerialName("context_length")`) | em andamento | /verify |
| P10 | Rodar `lintDebug` no CI | em andamento | /verify |
| P11 | F13 — preparação para Windows (Compose Desktop, JNA `SendInput`, DPAPI) | em andamento | PLAN |
| P12 | Validar F13 num Windows real (inserção, captura, cofre) | pendente | F13 |
| P13 | F14 — higiene de licença e entrega: `LICENSE`, notices finais, documentação de release | pendente (depende de P08) | PLAN |
| P14 | Persistência em SQLDelight/SQLite, como decidido no PLAN (hoje notas e dicionário são JSON em SharedPreferences) | pendente | PLAN |
| P15 | Evidência em aparelho real de F06 a F12 (os docs dessas fases não registram validação no S26 Ultra) | pendente | docs/tasks |
| P16 | Docs desatualizados: `AGENTS.md` ("ainda não há código"), F01 (SDK 36), F02/PLAN (API 30+ em vez de 33+) | em andamento | /verify |
| P17 | `README.md` com instalação, configuração (chave OpenRouter, acessibilidade, overlay) e build | pendente | revisão |

## Baixa

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P18 | Regressão do F02 em apps-alvo adicionais (WhatsApp, Chrome, Obsidian) | pendente | PLAN |
| P19 | Ícone do app (`MissingApplicationIcon` no lint) | pendente | lint |
