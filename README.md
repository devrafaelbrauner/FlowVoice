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
| Android: bolha arrastável que digita direto no campo aberto, com prévia (ou barra com Inserir) | funcional; arraste e prévia vistos no S26, falta validar a digitação com fala real |
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

Instale o **release assinado**, não o APK debug. A assinatura fixa e o app não
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
  estão configurados. O APK debug não é mais publicado.

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
   outros apps") e, se quiser, o Google Web Client ID. "Revisar antes de inserir"
   (desligado por padrão) troca a digitação direta pela barra com Inserir; a revisão
   por IA vale no fim do ditado pela bolha, nesse modo e nas notas.

## Ditando

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

## Licença

A definir (ver `TAREFAS_PENDENTES.md`, P08). Dependências, fontes e referências:
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) e [`ATTRIBUTIONS.md`](ATTRIBUTIONS.md).
