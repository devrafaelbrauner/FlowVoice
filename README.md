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
| Android: ditado, transcrição incremental, dicionário, notas, revisão | funcional |
| Android: barra de ditado sobre o app aberto (Inserir no campo ativo) | funcional; falta validar com fala real e o visual no aparelho |
| Login Google | opcional e local, sem validação do token em backend |
| Sincronização | motor pronto, servidor remoto ainda em memória |
| Windows (`desktopApp`) | compila; inserção, cofre e microfone só validados num Windows real |

## Estrutura

- `shared/` — Kotlin Multiplatform (Android + JVM desktop): sessão de ditado,
  cliente OpenRouter, pipeline, dicionário, notas, sync.
- `androidApp/` — app Android em Compose (`ui/theme`, `ui/components`,
  `ui/screens`, `ui/overlay`), serviço de acessibilidade e botão flutuante.
- `desktopApp/` — app Compose Desktop (F13).

## Requisitos

- JDK 17
- Android SDK com a plataforma 35 (`local.properties` com `sdk.dir=...`)
- Chave da OpenRouter

## Build e testes

```bash
./gradlew :shared:desktopTest          # testes do shared
./gradlew :androidApp:testDebugUnitTest # testes das telas e componentes
./gradlew :desktopApp:desktopTest      # testes do app desktop
./gradlew :androidApp:assembleDebug    # APK debug
./gradlew :androidApp:lintDebug        # lint Android
./gradlew :desktopApp:run              # app desktop
```

O CI (GitHub Actions) roda build, testes, lint, o jar do desktop e confere que o
runtime empacotado do desktop inclui `java.net.http`.

## Instalação no Android

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

`install -r` preserva os dados do app, inclusive a chave salva.

## Primeiro uso

1. **Login:** "Continuar com o Google". Sem Google Web Client ID configurado, segue
   direto (o login ainda não tem backend).
2. **Onboarding:** ligue o serviço de acessibilidade, conceda o microfone e salve a
   chave OpenRouter em Ajustes. A chave fica cifrada no aparelho, fora do backup e
   da sincronização, e só aparece mascarada.
3. **Ajustes:** ligue o botão flutuante (pede microfone, notificações e "sobrepor a
   outros apps"), a revisão por IA e, se quiser, o Google Web Client ID.

## Ditando

- **Pelo Início:** toque no microfone. O FlowVoice volta para o app em que você
  estava e abre a barra de ditado acima do teclado.
- **Pela bolha flutuante:** toque na bolha no app em que está digitando.
- Na barra: o texto aparece ao vivo (o trecho ainda não revisado fica pontilhado).
  "Inserir" encerra a gravação, mostra o texto final e, com outro toque, escreve no
  campo focado. "Cancelar" descarta.
- **Nas Notas:** "Nova nota" ou o microfone do detalhe ditam direto no corpo da nota.

## Licença

A definir (ver `TAREFAS_PENDENTES.md`, P08). Dependências, fontes e referências:
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) e [`ATTRIBUTIONS.md`](ATTRIBUTIONS.md).
