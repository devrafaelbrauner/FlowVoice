# FlowVoice

Ditado por voz para texto em pt-BR, com transcrição **no próprio aparelho** (NVIDIA
Nemotron 3.5 ASR Streaming, pelo [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), com o
texto aparecendo enquanto você fala) ou **na nuvem** via [OpenRouter](https://openrouter.ai).
O texto cai direto no campo em que você está
digitando, sem trocar o teclado. Android primeiro (Galaxy S26 Ultra); Windows em
preparação. Uso pessoal.

Plano e fases: [`docs/PLAN.md`](docs/PLAN.md) e [`docs/tasks/`](docs/tasks/README.md).
Histórico: [`CHANGELOG.md`](CHANGELOG.md). O que falta:
[`TAREFAS_PENDENTES.md`](TAREFAS_PENDENTES.md).

## Estado

| Área | Situação |
| --- | --- |
| Android: ditado, transcrição incremental, dicionário, notas, revisão | funcional |
| Android: bolha arrastável que digita direto no campo aberto, com prévia (ou barra com Inserir) | funcional; arraste e prévia vistos no S26, falta validar a digitação com fala real |
| Android: motor no aparelho (Nemotron 3.5, ao vivo, sem internet e sem chave) | funcional no S26 com frases tocadas por alto-falante (0.7.0); fala ao vivo, memória e bateria não medidas; termos médicos fracos (sem hotwords) |
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
- Para ditar: a chave da OpenRouter (nuvem) **ou** o modelo no aparelho (475 MB, baixado
  pelo próprio app; só Android arm64)

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

**Para colegas (recomendado): teste interno do Google Play.** Instalar pela Play Store
não é sideloading. A expectativa é que isso evite o alerta do Play Protect para app
desconhecido e o passo "Permitir configurações restritas", mas ainda não foi
conferido num aparelho. O passo a passo (conta, Play App Signing, `.aab` do CI,
formulários, política de privacidade, testadores) está em
[`docs/DISTRIBUICAO_PLAY.md`](docs/DISTRIBUICAO_PLAY.md). Até a Play Console estar
pronta, este caminho ainda não existe.

**Para o dono do repositório: release assinado pelo `adb`** (abaixo). Instale o
**release assinado**, não o APK debug. A assinatura fixa e o app não
depurável reduzem a chance de o Play Protect ou a proteção da Samsung acusarem o
FlowVoice (P161).

1. **Chave de release** (uma vez por máquina): um keystore fora do repositório,
   por exemplo `~/.android/flowvoice-release.jks`, e um `keystore.properties` na
   raiz (ignorado pelo git):

   ```properties
   storeFile=/Users/<você>/.android/flowvoice-release.jks
   storePassword=...
   keyAlias=flowvoice
   keyPassword=...
   ```

   No lugar do arquivo valem as variáveis `FLOWVOICE_KEYSTORE_FILE`,
   `FLOWVOICE_KEYSTORE_PASSWORD`, `FLOWVOICE_KEY_ALIAS` e `FLOWVOICE_KEY_PASSWORD`.
   Sem nenhum dos dois, o release sai sem assinatura e não instala.
2. **Build e instalação:**

   ```bash
   ./gradlew :androidApp:assembleRelease
   adb install -r androidApp/build/outputs/apk/release/androidApp-release.apk
   ```

   `install -r` preserva os dados do app, inclusive a chave salva.

- **Guarde uma cópia do keystore e da senha** fora do computador. Sem eles não há
  atualização: um APK com outra chave só entra desinstalando e perdendo os dados.
- **Troca de debug para release (uma vez):** o Android recusa atualizar um app com
  certificado diferente. Rode `adb uninstall dev.rafaelbrauner.flowvoice` antes do
  primeiro `install`. Isso apaga a chave OpenRouter salva, as notas e o dicionário:
  salve de novo a chave em Ajustes e religue a acessibilidade.
- **Instale pelo `adb`, não abrindo o APK** no navegador, no Arquivos, no Drive ou
  no WhatsApp. Isso é "Internet-sideloading": no Brasil, a proteção reforçada contra
  fraude do Play Protect bloqueia por esse caminho apps que pedem acessibilidade
  ([guia do Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance),
  [anúncio do piloto](https://security.googleblog.com/2024/02/piloting-new-ways-to-protect-Android-users-from%20financial-fraud.html)).
- O CI publica o artefato `flowvoice-release` (APK assinado + `.sha256`) em `main`
  quando os segredos `FLOWVOICE_KEYSTORE_BASE64` (keystore em Base64),
  `FLOWVOICE_KEYSTORE_PASSWORD`, `FLOWVOICE_KEY_ALIAS` e `FLOWVOICE_KEY_PASSWORD`
  estão configurados, junto com o `.aab` assinado para a Play
  (`./gradlew :androidApp:bundleRelease` gera o mesmo localmente). O APK debug não é
  mais publicado. O app mira o Android 16 (`compileSdk`/`targetSdk` 36).

### Se o Play Protect ou a Samsung acusarem

1. Anote qual proteção avisou (Play Protect ou "Proteção do dispositivo" da
   Samsung) e a mensagem exata.
2. Calcule o SHA-256 do APK instalado (`shasum -a 256 androidApp-release.apk`),
   envie o APK ao VirusTotal e peça revisão no
   [formulário de apelação do Play Protect](https://support.google.com/googleplay/android-developer/contact/protectappeals),
   com o pacote `dev.rafaelbrauner.flowvoice` e esse SHA-256.
3. Alternativa: distribuir pela Play Console em teste interno ou pelo
   [compartilhamento interno de apps](https://play.google.com/console/about/internalappsharing/);
   a instalação pela Play não é sideloading.
4. [Verificação de desenvolvedor](https://developer.android.com/developer-verification):
   a partir de 2026-09-30, no Brasil, apps vindos de lojas participantes exigem
   desenvolvedor verificado. `adb install` continua funcionando; registrar o pacote
   com esta chave garante a instalação fora do `adb` no futuro.

## Primeiro uso

1. **Login:** sem Google Web Client ID configurado (o normal), o botão é
   **Começar** e segue direto. Com um Client ID em Ajustes, aparece "Continuar com o
   Google". A sincronização entre aparelhos ainda não está ativa: notas e
   dicionário ficam no celular.
2. **Onboarding (três passos):** o botão escuro leva ao próximo passo pendente, e
   "Pular por enquanto" fica como ação secundária. Quando os três passos ficam ok, o
   onboarding se dá por concluído sozinho. Pular também fica guardado, e ele não
   volta a cada abertura. O que faltar continua no Início (pílula de status e
   microfone) e em Ajustes.
   1. **Acessibilidade:** antes do Android, abre a tela **"O que o FlowVoice vê"**.
      Ela diz o que o serviço acessa (o nome do app na tela; no ditado, o campo com
      o cursor; a posição de campo, cursor e teclado) e para quê, avisa que o
      Android vai falar em "controle total" e tem o link da política de
      privacidade. A Acessibilidade só abre com **"Entendi, abrir Acessibilidade"**.
      O botão do Diagnóstico passa pela mesma tela.
      - **Instalado fora de loja, Android 13+:** o onboarding mostra, antes, os
        passos das configurações restritas. (1) Tente ligar pelo passo 1: o Android
        diz que o acesso foi negado e fala em risco às suas informações, o aviso
        padrão para app de fora de loja. (2) Toque em **Abrir detalhes do app** →
        ⋮ → **Permitir configurações restritas** (pode pedir o PIN). (3) Volte e
        toque no passo 1 de novo.
      - **Não** ponha o FlowVoice como atalho de acessibilidade (botão ou gesto): o
        atalho alterna o serviço e um toque acidental o desliga.
   2. **Microfone:** grava só durante o ditado.
   3. **Chave OpenRouter ou modelo no aparelho:** qualquer um dos dois basta. A tela
      própria tem, embaixo, **"Baixar o modelo (475 MB)"**: o motor no aparelho funciona
      sem internet e sem custo, e o áudio não sai do celular (ver "Motor de transcrição").
      Para a nuvem, a mesma tela explica a OpenRouter, abre
      openrouter.ai/keys e avisa dos créditos pré-pagos no cartão (compra mínima
      de US$ 5). Mostra uma **estimativa** de custo: ≈ R$ 1,90 a 2,70 por hora de
      ditado (set/2026), calculada com os preços da OpenRouter, as taxas e o câmbio
      de 2026-09-25, e não medida em uso real. "Validar e salvar" é o mesmo de
      Ajustes. A chave fica cifrada no aparelho, fora do backup e da sincronização,
      e só vai à OpenRouter. Ditar sem chave nem modelo mostra o aviso "Falta configurar
      a transcrição", e **Configurar** abre essa mesma tela.
3. **Ajustes:** ligue o botão flutuante (pede microfone, notificações e "sobrepor a
   outros apps"; na volta da permissão ele liga sozinho) e, se quiser, o Google Web
   Client ID. "Revisar antes de inserir" (desligado por padrão) troca a digitação
   direta pela barra com Inserir. A revisão por IA vale no fim do ditado pela bolha,
   nesse modo e nas notas. A política de privacidade também está em Ajustes.

## Motor de transcrição

Em **Ajustes → Motor de transcrição**:

- **No aparelho (Nemotron, ao vivo):** NVIDIA Nemotron 3.5 ASR Streaming 0.6B (int8) pelo
  sherpa-onnx, em `pt-BR`. O modelo (475 MB) é baixado uma vez do GitHub
  (k2-fsa/sherpa-onnx), com o SHA-256 conferido, e ocupa 682 MB no armazenamento privado do
  app, fora do backup; durante a instalação são precisos ~1,2 GB livres. Funciona **sem
  internet e sem chave**: o áudio é transcrito no celular e não sai dele. O que está sendo dito
  aparece como provisório (pontilhado) no cartão da bolha, na barra e na nota, e só entra no
  campo quando fecha num pedaço — na pausa, ou, falando sem parar, em palavras inteiras a cada
  ~4 s. O primeiro ditado depois de abrir o app espera ~1,5 s pela carga do modelo (o que você
  fala nesse tempo não se perde); o modelo sai da memória depois de 5 min sem ditado. Um ditado
  no aparelho dura no máximo 10 min. Se o motor falhar no meio, o texto até ali é entregue com
  aviso, e o app **não** passa sozinho para a nuvem. Sem hotwords: termos médicos saem pior que
  na nuvem.
- **Nuvem (OpenRouter):** o padrão, como antes; precisa da chave.
- A **revisão por IA** (Ajustes) envia o texto à OpenRouter nos dois motores e precisa da
  chave; sem ela, é pulada.
- "Apagar modelo" volta o motor para a nuvem.

## Ditando

- **Toque para ditar, toque de novo para parar:** não é preciso segurar o botão.
  Com o FlowVoice aberto na tela, a bolha ociosa fica escondida e volta quando você
  sai do app. O serviço segue ligado.
- **Pela bolha flutuante:** toque na bolha no app em que está digitando. Cada trecho
  vai até uma pausa natural da fala (300 ms de pausa depois de ao menos ~1 s de
  áudio) ou, falando sem pausa, até ~4 s. Ele é **digitado no campo** assim que é
  transcrito, em ordem e com espaço entre os trechos. Toque na bolha de novo para encerrar: o último trecho
  entra em seguida. Com a **revisão por IA** ligada (Ajustes), o ditado inteiro é
  revisto de uma vez no fim — só pontuação, maiúsculas e acentos, sem trocar palavra —
  e substitui no campo o que o FlowVoice tinha escrito; a prévia mostra "revisando…"
  durante o ~1 s da chamada. Se a revisão falhar, se o texto não estiver mais logo
  antes do cursor ou se o ditado passar de 4000 caracteres, fica como foi digitado.
- **Prévia:** um cartão junto à bolha mostra o cronômetro e o estado. Ele abre acima
  ou abaixo da linha do cursor, **nunca sobre ela** nem sobre o teclado; se o cursor
  anda, o cartão muda de lado. Com pouco espaço, encolhe e os textos rolam; sem
  espaço nenhum, some até sobrar lugar. Em **NO CAMPO** fica o que já foi escrito,
  depois vêm "transcrevendo…" e o aviso de trecho com falha (timeout, sem
  créditos…). "Cancelar" descarta só o que ainda não foi digitado; o que está no
  campo fica.
- **Troca de app ou de campo:** se o foco for para outro app, outra conversa ou uma
  tela sem campo, nada mais é digitado sozinho, **mesmo que você volte**. O resto
  aparece como **PENDENTE**, e "Inserir aqui" o escreve no app em foco e retoma a
  digitação nesse campo. Um toque até 1 s depois da pausa é ignorado.
- **Pelo Início:** toque no microfone. O FlowVoice reabre o último app em que você
  estava (o serviço de acessibilidade anota o app da janela ativa; o launcher e o
  próprio FlowVoice não contam) e digita só nesse app. Sem app anotado, volta para
  a tela anterior, e o primeiro trecho espera "Inserir aqui".
- **Mover a bolha:** arraste-a para qualquer ponto. Ao soltar, ela encosta na borda
  mais próxima, e a posição fica salva, inclusive ao girar a tela. Com TalkBack, use
  as ações "Mover para cima", "Mover para baixo" e "Mover para o outro lado".
- **Revisar antes de inserir** (Ajustes): volta ao fluxo com barra acima do teclado.
  O texto aparece ao vivo (o trecho ainda não revisado fica pontilhado). "Inserir"
  encerra a gravação e mostra o texto final, com a revisão por IA se ligada, e outro
  toque o escreve no campo focado. "Cancelar" descarta. Se o app em foco mudou antes
  do toque em "Inserir", nada é escrito: o texto fica na barra, e o aviso diz que um
  novo toque escreve no app atual. Um toque até 1 s depois da recusa (toque duplo) é
  ignorado.
- **Nas Notas:** "Nova nota" ou o microfone do detalhe ditam direto no corpo da nota.
- **Pontuação falada:** diga "vírgula", "ponto final", "dois pontos", "ponto e vírgula",
  "interrogação", "exclamação", "reticências", "abre/fecha aspas", "abre/fecha parênteses",
  "nova linha" e "novo parágrafo". Para lista, "novo item" ou "próximo item" começam uma linha
  com "- ". "Ponto" sozinho continua palavra ("ponto de ônibus"), e o nome do sinal depois de
  artigo é fala ("coloca uma vírgula"). O sinal aparece quando o trecho fecha; na prévia ao vivo
  o comando ainda está escrito por extenso.

## Licença

A definir (ver `TAREFAS_PENDENTES.md`, P08). Dependências, fontes e referências:
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) e [`ATTRIBUTIONS.md`](ATTRIBUTIONS.md).
