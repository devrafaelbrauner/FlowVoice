# Tarefas

Rastreamento das fases do [`PLAN.md`](../PLAN.md). Cada fase tem um arquivo
`Fxx.md` com as **sub-tarefas** e os **critérios de aceite** da fase.

**Legenda de status:** `[ ]` pendente · `[~]` em andamento · `[x]` concluída ·
`[!]` bloqueado

## Mapa de fases

| Fase | Título | Arquivo | Status |
| --- | --- | --- | --- |
| F00 | Governança e preparação do repositório | [F00.md](F00.md) | [~] |
| F01 | Scaffold Kotlin Multiplatform + Gradle | [F01.md](F01.md) | [x] |
| F02 | POC de inserção direta (aparelho real) | [F02.md](F02.md) | [x] |
| F03 | Captura de áudio contínua + sessão de ditado | [F03.md](F03.md) | [x] |
| F04 | Integração OpenRouter (transcrição incremental) | [F04.md](F04.md) | [x] |
| F05 | Benchmark de modelos de transcrição (real) | [F05.md](F05.md) | [x] |
| F06 | Texto ao vivo (prévia) | [F06.md](F06.md) | [x] |
| F07 | Dicionário pessoal + aprovações | [F07.md](F07.md) | [x] |
| F08 | Notas | [F08.md](F08.md) | [x] |
| F09 | Pontuação e ortografia | [F09.md](F09.md) | [x] |
| F10 | Login Google obrigatório | [F10.md](F10.md) | [x] |
| F11 | Sincronização (offline-first) | [F11.md](F11.md) | [x] |
| F12 | UX do app principal | [F12.md](F12.md) | [x] |
| F13 | Preparação para Windows | [F13.md](F13.md) | [~] |
| F14 | Higiene de licença e entrega | (a criar) | [ ]

## Convenção de sub-tarefas

- `Fxx.y` identifica uma unidade de trabalho atomicizável dentro da fase.
- Cada sub-tarefa tem um critério de aceite testável.
- Ao concluir uma fase, atualizar o **Status** na tabela acima.