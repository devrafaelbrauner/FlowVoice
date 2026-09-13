# FlowVoice

Ditado por voz para texto em pt-BR, com transcrição na nuvem via
[OpenRouter](https://openrouter.ai). O texto cai direto no campo em que você está
digitando, sem trocar o teclado. Android primeiro (Galaxy S26 Ultra); Windows em
preparação. Uso pessoal.

Plano e fases: [`docs/PLAN.md`](docs/PLAN.md) e [`docs/tasks/`](docs/tasks/README.md).
Histórico: [`CHANGELOG.md`](CHANGELOG.md). O que falta:
[`TAREFAS_PENDENTES.md`](TAREFAS_PENDENTES.md).

## Estado

| Área | Situação |
| --- | --- |
| Android: ditado, transcrição incremental, prévia, dicionário, notas, revisão | funcional |
| Android: botão flutuante que dita no app aberto | funcional; falta validar com fala real |
| Login Google | local, sem validação do token em backend |
| Sincronização | motor pronto, servidor remoto ainda em memória |
| Windows (`desktopApp`) | compila; inserção, cofre e microfone só validados num Windows real |

## Estrutura

- `shared/` — Kotlin Multiplatform (Android + JVM desktop): sessão de ditado,
  cliente OpenRouter, pipeline, dicionário, notas, sync.
- `androidApp/` — app Android (Compose), serviço de acessibilidade e botão flutuante.
- `desktopApp/` — app Compose Desktop (F13).

## Requisitos

- JDK 17
- Android SDK com a plataforma 35 (`local.properties` com `sdk.dir=...`)
- Chave da OpenRouter

## Build e testes

```bash
./gradlew :shared:desktopTest          # testes do shared
./gradlew :androidApp:assembleDebug    # APK debug
./gradlew :androidApp:lintDebug        # lint Android
./gradlew :desktopApp:run              # app desktop
```

O CI (GitHub Actions) roda os quatro passos acima (com `desktopJar` no lugar de `run`).

## Instalação no Android

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

`install -r` preserva os dados do app, inclusive a chave salva.

## Configuração no app

1. **Chave OpenRouter:** em "Nova chave OpenRouter", toque em "Validar e salvar
   chave". A chave fica cifrada no aparelho, não entra em logs nem na sincronização
   e fica fora do backup.
2. **Acessibilidade:** "Abrir configurações de acessibilidade" → Serviços
   instalados → FlowVoice. É por esse serviço que o texto é inserido no campo focado.
3. **Botão flutuante:** ligue na tela principal e conceda microfone, notificações e
   "sobrepor a outros apps".
4. **Opcional:** revisão de pontuação e ortografia (toggle) e Google Web Client ID
   para o login.

## Uso do botão flutuante

- Um toque começa a gravar (`● Gravando`).
- Outro toque finaliza, transcreve e insere o texto no campo focado do app aberto.
- Um toque longo cancela sem inserir.

## Licença

A definir (ver `TAREFAS_PENDENTES.md`, P08). Dependências e referências:
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) e [`ATTRIBUTIONS.md`](ATTRIBUTIONS.md).
