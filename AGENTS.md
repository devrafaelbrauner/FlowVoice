# AGENTS.md

Orientações para agentes (Codex/Claude/etc.) que operam neste repositório.
Leia antes de alterar código.

## O que é o FlowVoice

App de ditado **voz → texto** em nuvem (**OpenRouter**). **Android primeiro**
(Galaxy S26 Ultra), **pt-BR**, **uso pessoal**, preparado para **Windows**.
Fonte de verdade do roadmap: [`docs/PLAN.md`](docs/PLAN.md).

## Stack

- **Kotlin Multiplatform** + **Compose Multiplatform** / Jetpack Compose.
- **Ktor Client** (OpenRouter), **Kotlin Serialization**.
- **SQLDelight / SQLite** para estado local (offline-first).
- **GitHub Actions** (CI).
- Windows: Compose Desktop + JNA / `SendInput` (fase F13).

> Através da fase **F01**, ainda não há código; os comandos abaixo são os
> **alvo** da scaffold Gradle e devem ser verificados na criação dos builds.

## Comandos (alvo, após F01)

- Build:      `./gradlew build`
- Testes:     `./gradlew test` (ou `testDebug`)
- Lint:       `./gradlew lint` / `./gradlew detekt` (se adotado)
- Build App:  `./gradlew :androidApp:assembleDebug`
- CI: GitHub Actions em `main` e em PRs.

## Convenções de código

- **Kotlin oficial** (idiomático); sem comentários desnecessários.
- Reuse de libs já presentes no projeto; não adicione dependência sem motivo.
- Componentes novos seguem o estilo dos vizinhos (naming, tipagem, estrutura).
- **Offline-first**: estado local é a fonte de verdade; sync é reconciliação em
  background, nunca no caminho crítico do ditado.

## Regras obrigatórias (nÃO violar)

1. **Versionamento Git**: commits pequenos e atômicos por unidade de trabalho.
2. **Nunca copiar código** de projetos **GPL/AGPL** (ver
   `docs/references.md`). Inspiração de arquitetura/UX apenas.
3. **Atualizar atribuições**: qualquer mudança que introduza dependências ou
   referências deve atualizar `THIRD_PARTY_NOTICES.md` / `ATTRIBUTIONS.md` /
   `docs/references.md`.
4. **Segurança**: nunca commitar segredos; a **chave OpenRouter fica local e
   cifrada** e **nunca** é sincronizada nem logada.
5. **Licença**: FlowVoice sob **MIT (proposta, pendente confirmação)**. Sem
   copiar sob licença incompatível.

## Prioridades de UX (contexto para decisões)

1. **Inserção direta no campo ativo** (mantendo o teclado atual
   `IntelligentKeyboard`) — prioridade máxima.
2. **Texto ao vivo** com distinção clara **provisório vs finalizado**.
3. Latência do ditado **independente** de rede de sincronização.

## Verificação

- Antes de entregar: `./gradlew build` + testes passando (quando houver build).
- Meticular a POC de inserção **no aparelho real** (F02) — não confiar só no
  emulador.
- Benchmark de modelos **no aparelho real** com limite de custo (F05).