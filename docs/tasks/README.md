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
| F07 | Dicionário pessoal + aprovações | (a criar) | [ ] |
| F08 | Notas | (a criar) | [ ] |
| F09 | Pontuação e ortografia | (a criar) | [ ] |
| F10 | Login Google obrigatório | (a criar) | [ ] |
| F11 | Sincronização (offline-first) | (a criar) | [ ] |
| F12 | UX do app principal | (a criar) | [ ] |
| F13 | Preparação para Windows | (a criar) | [ ] |
| F14 | Higiene de licença e entrega | (a criar) | [ ]

## Convenção de sub-tarefas

- `Fxx.y` identifica uma unidade de trabalho atomicizável dentro da fase.
- Cada sub-tarefa tem um critério de aceite testável.
- Ao concluir uma fase, atualizar o **Status** na tabela acima.