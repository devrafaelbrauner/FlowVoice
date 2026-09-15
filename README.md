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
   - **Instalado por adb ou APK (fora de loja), Android 13+:** o sistema bloqueia o
     interruptor do serviço ("configurações restritas"). Tente ligar uma vez e depois
     vá em Configurações → Aplicativos → FlowVoice → ⋮ → **Permitir configurações
     restritas**; aí ligue o FlowVoice em Acessibilidade. O onboarding mostra esse
     passo e um atalho para os detalhes do app.
   - **Não** ponha o FlowVoice como atalho de acessibilidade (botão ou gesto): o
     atalho alterna o serviço e um toque acidental o desliga.
3. **Ajustes:** ligue o botão flutuante (pede microfone, notificações e "sobrepor a
   outros apps"), a revisão por IA e, se quiser, o Google Web Client ID.

## Ditando

- **Pelo Início:** toque no microfone. O FlowVoice reabre o último app em que você
  estava (o serviço de acessibilidade anota o app da janela ativa; o launcher e o
  próprio FlowVoice não contam) e abre a barra de ditado acima do teclado. Sem app
  anotado, volta para a tela anterior.
- **Pela bolha flutuante:** toque na bolha no app em que está digitando.
- Na barra: o texto aparece ao vivo (o trecho ainda não revisado fica pontilhado).
  "Inserir" encerra a gravação, mostra o texto final e, com outro toque, escreve no
  campo focado. "Cancelar" descarta.
- Se o app em foco mudou antes do toque em "Inserir", nada é escrito: o texto
  fica na barra, e o aviso diz que um novo toque escreve no app atual. Um toque
  até 1 s depois da recusa (toque duplo) é ignorado.
- **Nas Notas:** "Nova nota" ou o microfone do detalhe ditam direto no corpo da nota.

## Licença

A definir (ver `TAREFAS_PENDENTES.md`, P08). Dependências, fontes e referências:
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) e [`ATTRIBUTIONS.md`](ATTRIBUTIONS.md).
