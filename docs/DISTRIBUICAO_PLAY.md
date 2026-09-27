# Distribuição pelo Google Play (teste interno)

Objetivo: colegas instalam o FlowVoice **pela Play Store** em vez de APK avulso, sem o
alerta do Play Protect para app desconhecido e sem o passo "Permitir configurações
restritas". Fontes conferidas em 2026-09-27; cada item cita a página oficial.

Legenda: **[VOCÊ]** = ação sua no Play Console/navegador (conta, pagamento,
formulários). **[REPO]** = já feito ou feito por comando neste repositório.

## 0. Bloqueios antes do primeiro envio

- [ ] **[REPO — pendente] `targetSdk` 36.** Desde 31/08/2026, apps novos e
  atualizações precisam mirar o Android 16 (API 36); a página não abre exceção para
  faixas de teste, só para apps privados permanentes. O `androidApp` hoje está com
  `compileSdk = 35` / `targetSdk = 35`: o Play Console deve recusar o `.aab` até isso
  subir para 36 (com teste no aparelho, porque o Android 16 muda comportamento).
  Dá para pedir prorrogação até 01/11/2026 no Console.
  [Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [ ] **[REPO — pendente] Divulgação em destaque da acessibilidade** dentro do app
  (ver §5.3). O onboarding atual só diz "É o que permite escrever no campo ativo sem
  trocar seu teclado": não tem consentimento afirmativo nem descreve os dados.
- [ ] **[REPO — pendente] Link da política de privacidade dentro do app** (§5.1).

## 1. Conta de desenvolvedor — [VOCÊ]

- [ ] Criar a conta em <https://play.google.com/console/signup> (18+ anos), aceitar o
  Developer Distribution Agreement e pagar a **taxa única de US$ 25** (cartão de
  crédito/débito; pré-pago não é aceito).
- [ ] Escolher o tipo **Pessoal** ou **Organização** e concluir a **verificação de
  identidade** (pode pedir documento oficial e cartão no seu nome legal; taxa não é
  reembolsada se os dados forem inválidos). Organização exige número D-U-N-S.
- [ ] Conta pessoal nova: verificar acesso a um aparelho Android pelo app Play
  Console. O requisito de **12 testadores por 14 dias em teste fechado** vale só para
  liberar **produção**; o teste interno não depende dele.

Fontes: [Get started with Play Console](https://support.google.com/googleplay/android-developer/answer/6112435),
[App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465),
[FAQ da verificação (D-U-N-S)](https://developer.android.com/developer-verification/guides/faq).

### Verificação de desenvolvedor Android (Brasil, 30/09/2026)

- A partir de **30/09/2026**, no **Brasil**, Indonésia, Singapura e Tailândia, apps
  instalados por **lojas participantes** (Google Play incluído) em aparelhos
  certificados com Android 7+ precisam ser de desenvolvedor verificado e ter o pacote
  registrado; expansão global em 2027.
  [Android developer verification](https://developer.android.com/developer-verification)
- **A conta do Play Console cobre isso:** a identidade verificada do Play já atende ao
  requisito e, **para apps novos, o Play registra o nome do pacote automaticamente**
  ao criar o app. Confira o status na página "Android developer verification" do
  Console. [Register on Google Play Console](https://developer.android.com/developer-verification/guides/google-play-console),
  [FAQ](https://developer.android.com/developer-verification/guides/faq)
- Com **Play App Signing** o app entra no registro automático.
  [FAQ](https://developer.android.com/developer-verification/guides/faq)
- APK avulso/sideload **ainda não** é bloqueado em 30/09/2026 (só lojas
  participantes); instalação por `adb` segue livre de verificação.
  [FAQ](https://developer.android.com/developer-verification/guides/faq)

## 2. Criar o app — [VOCÊ]

- [ ] Play Console → **Home → Create app**: idioma padrão pt-BR, nome "FlowVoice",
  App, Gratuito, e-mail de contato; aceitar as declarações e os **termos do Play App
  Signing**. [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152)
- [ ] O pacote **não é digitado no formulário**: ele é fixado pelo **primeiro `.aab`
  enviado** (`dev.rafaelbrauner.flowvoice`) e não muda mais. Nomes de pacote são
  permanentes. [Set up an internal test](https://support.google.com/googleplay/android-developer/answer/9845334#internal_test),
  [Create and set up your app](https://support.google.com/googleplay/android-developer/answer/9859152)

## 3. Play App Signing e chave de upload

- **[REPO]** A chave local `~/.android/flowvoice-release.jks` (alias `flowvoice`,
  RSA 4096, `CN=Rafael Brauner, O=FlowVoice, C=BR`) assina o `.aab` e passa a ser a
  **chave de upload**. SHA-256 do certificado:
  `0F:25:B5:04:2D:12:88:E1:75:0F:27:08:1B:5A:52:0F:B9:34:8F:33:50:9E:BF:3F:D1:DE:2B:58:4D:21:DC:F4`.
- Em app novo, o Google gera a **chave de assinatura do app** e assina os APKs
  entregues aos aparelhos; você só assina o upload. Chave de upload perdida pode ser
  redefinida pelo Google. [Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [ ] **[VOCÊ]** Se o Console pedir o certificado de upload (ou para uma redefinição),
  exporte-o; o `keytool` pede a senha no terminal, **não** a passe na linha de comando:

  ```sh
  keytool -export -rfc -keystore ~/.android/flowvoice-release.jks -alias flowvoice -file upload_certificate.pem
  ```

  [Use Play App Signing — Request an upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756)
- [ ] **[VOCÊ] Login com Google:** o app instalado pela Play é assinado pela chave do
  Google, não pela sua. Cadastre as impressões digitais da **chave de assinatura do
  app** (Protected with Play → Play app signing) no cliente OAuth Android do Google
  Cloud; no compartilhamento interno de app, também o certificado de teste dele
  (§6). [Use Play App Signing — Register with API providers](https://support.google.com/googleplay/android-developer/answer/9842756)

## 4. Gerar o bundle — [REPO]

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=~/Library/Android/sdk
./gradlew :androidApp:bundleRelease
jarsigner -verify -verbose -certs androidApp/build/outputs/bundle/release/androidApp-release.aab | tail -20
```

- Saída: `androidApp/build/outputs/bundle/release/androidApp-release.aab`, assinado
  com a chave de `keystore.properties` (ignorado pelo git).
- No CI, pushes em `main` com os segredos `FLOWVOICE_*` publicam o artefato
  `flowvoice-release` com o APK, o `.aab` e os `.sha256` de cada um.
- **Regra do `versionCode`:** cada `.aab` enviado a uma faixa de teste ou produção
  precisa de `versionCode` **maior** que todos já enviados; o Play recusa um código
  repetido. Suba `versionCode` (e `versionName`) em `androidApp/build.gradle.kts`
  antes de cada envio. O compartilhamento interno de app é a exceção: aceita código
  repetido. [Version your app](https://developer.android.com/studio/publish/versioning),
  [Share app bundles and APKs internally](https://support.google.com/googleplay/android-developer/answer/9844679)

## 5. Conteúdo do app (Policy and programs → App content) — [VOCÊ]

O que o Play exige e o que se aplica ao teste interno:

| Item | Teste interno | Fonte |
|---|---|---|
| Política de privacidade (URL) | Exigida para apps com permissões/dados sensíveis (microfone, acessibilidade). A página não dispensa o teste interno | [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455) |
| Data safety | **Dispensada** enquanto o app estiver **só** na faixa de teste interno; obrigatória em teste fechado/aberto/produção | [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) |
| Declaração da API de acessibilidade | Obrigatória para app com `AccessibilityService`; nada na página a dispensa no teste interno | [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491) |
| Declaração de foreground service (tipo microphone) | Obrigatória para `targetSdk` 34+; nada na página a dispensa no teste interno | [Foreground service requirements](https://support.google.com/googleplay/android-developer/answer/13392821) |
| Público-alvo | Declarar faixa etária em app novo ou atualização | [Target audience](https://support.google.com/googleplay/android-developer/answer/9867159) |
| Anúncios, classificação de conteúdo | Parte do App content | [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455) |

Os testes internos "podem não passar" pela revisão padrão de política e segurança.
[Set up an internal test](https://support.google.com/googleplay/android-developer/answer/9845334#internal_test)

### 5.1 Política de privacidade

- [ ] **[VOCÊ]** Preencha os `[PREENCHER]` de
  [`POLITICA_PRIVACIDADE.md`](POLITICA_PRIVACIDADE.md), publique numa URL pública
  ativa (GitHub Pages, site) e informe em **App content → Privacy policy**.
- [ ] **[REPO — pendente]** Linkar a mesma URL **dentro do app** (Ajustes, por
  exemplo), exigido para apps com dados ou permissões sensíveis.
  [Prepare your app for review](https://support.google.com/googleplay/android-developer/answer/9859455)

### 5.2 Data safety (só ao sair do teste interno)

Rascunho fiel ao código: **áudio** ("Voice or sound recordings") e **texto ditado**
(conteúdo gerado pelo usuário) saem do aparelho para a OpenRouter durante o ditado ou
a revisão por IA; trafegam por HTTPS; o app não guarda o áudio. Processamento
efêmero ainda precisa entrar no formulário, embora possa não aparecer na loja.
[Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)

### 5.3 Acessibilidade: declaração e divulgação em destaque

- `accessibility_flowvoice.xml` **não** tem `isAccessibilityTool`: o FlowVoice é um
  app de ditado geral, não uma ferramenta primariamente para pessoas com deficiência.
  Vale a declaração de app que **não** é ferramenta de acessibilidade: finalidade
  "App functionality"; se coleta ou compartilha dados pela API, quais; e **link de
  vídeo** mostrando a divulgação em destaque no app.
- **Divulgação em destaque** obrigatória: dentro do app, no uso normal (não em menu),
  descrevendo os dados acessados e o uso, com **ação afirmativa** de consentimento
  ("Concordo"), separada de outras divulgações, e não só na política de privacidade.
  [Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491),
  [Best practices for prominent disclosure](https://support.google.com/googleplay/android-developer/answer/11150561)
- Uso real, para o texto da divulgação e da declaração: o serviço anota o pacote da
  janela ativa (eventos `typeWindowStateChanged`), localiza o campo com foco (posição,
  tipo, se é senha, tamanho do texto), lê o trecho antes do cursor para conferir o
  que o próprio FlowVoice escreveu e **insere** o texto ditado (`commitText` ou
  `ACTION_SET_TEXT`). Não insere em campo de senha. Nada disso sai do aparelho.

### 5.4 Foreground service (microfone)

- `FlowVoiceOverlayService` tem `foregroundServiceType="microphone"` e as permissões
  `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_MICROPHONE`. Na declaração: descrição
  (captura de voz para ditado iniciada pelo usuário na bolha), impacto se adiada ou
  interrompida (o ditado para ou não começa), **link de vídeo** e caso de uso.
  [Foreground service requirements](https://support.google.com/googleplay/android-developer/answer/13392821)

### 5.5 Sobreposição (`SYSTEM_ALERT_WINDOW`)

- Nas páginas conferidas **não** há formulário de declaração próprio para
  sobreposição. A política de permissões pede só que o app **leve o usuário à tela do
  sistema** para aprovar permissões especiais como `SYSTEM_ALERT_WINDOW`; Início e
  Ajustes já abrem essa tela. [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170)

### 5.6 Público-alvo

- [ ] **[VOCÊ]** Declarar **18+** (uso profissional entre colegas), sem apelo a
  crianças. [Target audience](https://support.google.com/googleplay/android-developer/answer/9867159)

## 6. Teste interno — [VOCÊ]

- [ ] **Test and release → Testing → Internal testing → Testers → Create email
  list** com os e-mails (conta Google) dos colegas; até **100 testadores** por app.
- [ ] Informar um e-mail/URL de feedback, **Create new release**, enviar o `.aab`,
  publicar. O primeiro envio fica disponível na hora, com nome temporário por até 48
  h; os seguintes, em minutos.
- [ ] Copiar o **link de opt-in** (só aparece com status "Published") e mandar aos
  colegas: cada um aceita o convite e instala pela Play Store. Quem está no teste
  interno recebe só o `versionCode` publicado nessa faixa.

[Set up an open, closed, or internal test](https://support.google.com/googleplay/android-developer/answer/9845334)

### Alternativa: compartilhamento interno de app (sem revisão)

- Upload em <https://play.google.com/console/internal-app-sharing/> gera um **link**
  na hora, restrito a listas de e-mail ou aberto a quem tiver o link.
- Limites: no máximo **100 downloads por link**, link **expira em 60 dias**,
  `versionCode` pode repetir, aceita build depurável, **não** vira release de faixa.
  O Google **reassina** com uma chave própria de compartilhamento interno (baixe o
  certificado se o Login com Google precisar dele).
- Cada colega precisa ativar uma vez: Play Store → Configurações → Sobre → tocar 7×
  na versão da Play Store → ligar **Compartilhamento interno de apps**.
- O testador precisa ter acesso à ficha do app na Play; app sem nenhuma faixa
  acessível a ele pode ficar indisponível.

[Share app bundles and APKs internally](https://support.google.com/googleplay/android-developer/answer/9844679)

## 7. O que o colega vê

- Abre o link de opt-in, aceita ser testador, instala o FlowVoice pela Play Store e
  recebe atualizações automaticamente. Não pode publicar avaliação pública.
  [Set up an open, closed, or internal test](https://support.google.com/googleplay/android-developer/answer/9845334)
- **Configurações restritas:** a Ajuda do Android descreve o bloqueio como proteção
  contra apps nocivos instalados no aparelho e indica procurar o app numa loja
  confiável como o Google Play. **A página não diz explicitamente** que apps
  instalados pela Play ficam isentos; o onboarding atual já trata instalação pela
  Play como isenta. Confirme no primeiro aparelho de colega.
  [Learn about restricted settings](https://support.google.com/android/answer/12623953)
- **Quem já tem o APK avulso** precisa **desinstalar** antes: a versão da Play vem
  assinada pela chave do Google, e o Android não atualiza um app instalado com outra
  assinatura. As notas, o dicionário e a chave ficam fora do backup e se perdem.
- Cada colega usa a **própria chave OpenRouter**, informada no app.
