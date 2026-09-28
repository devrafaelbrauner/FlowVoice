# Política de Privacidade do FlowVoice

Última atualização: [PREENCHER: data de publicação]

Esta política descreve como o aplicativo Android **FlowVoice**
(`dev.rafaelbrauner.flowvoice`) trata os seus dados.

## Quem é o responsável

- Controlador dos dados: [PREENCHER: nome completo ou razão social]
- Contato para privacidade: [PREENCHER: e-mail]
- Endereço: [PREENCHER: cidade/UF, ou endereço completo se exigido]

## Resumo

- O FlowVoice transforma a sua voz em texto de um de dois jeitos, à sua escolha: na
  nuvem, pela **OpenRouter** (<https://openrouter.ai>), com a **sua própria chave de
  API**; ou com o **modelo no aparelho**, que transcreve no próprio celular.
- Com o modelo no aparelho e a revisão final por IA desligada, o áudio **não sai do
  celular**. Com a revisão ligada, o áudio do ditado inteiro vai uma vez à OpenRouter
  **no fim de cada ditado**, mesmo com o modelo no aparelho. Com a transcrição na
  nuvem, o áudio sai do aparelho **enquanto você dita**. Em nenhum dos casos o app
  grava o áudio em arquivo.
- Notas, dicionário e a chave da OpenRouter ficam **só no seu aparelho** e fora do
  backup do Android.
- O app não tem anúncios, não usa SDKs de análise ou rastreamento e não tem servidor
  próprio: o desenvolvedor não recebe os seus dados.

## 1. Dados tratados e por quê

### 1.1 Áudio do microfone

- **Quando:** só depois que você inicia um ditado (bolha ou botão do microfone), e
  só enquanto ele dura. Com a bolha ativa, o app mantém um serviço em primeiro plano
  do tipo microfone, com notificação visível.
- **Para quê:** transcrever a fala.
  - **Modelo no aparelho:** o áudio é processado no próprio celular pelo modelo de
    reconhecimento de fala. Com a **revisão final por IA** desligada, ele nunca é
    enviado a ninguém. Com ela ligada, ao fim de cada ditado o áudio inteiro (em
    pedaços de até 50 s nos ditados longos) é convertido em WAV e enviado por HTTPS à
    OpenRouter, que o repassa ao provedor do modelo de transcrição (padrão:
    `openai/gpt-transcribe`); o texto que volta substitui o do aparelho.
  - **Nuvem (OpenRouter):** o áudio é dividido em trechos curtos, convertido em WAV e
    enviado por HTTPS à OpenRouter, que o repassa ao provedor do modelo de
    transcrição escolhido (padrão: `openai/gpt-transcribe`).
- **Armazenamento:** o áudio fica só na memória do app, até o próximo ditado (um
  ditado cancelado é descartado na hora); nada é gravado em arquivo no aparelho. Na
  tela de Diagnóstico, se você pedir um benchmark, o áudio da última sessão é
  reenviado à OpenRouter para comparar modelos.

### 1.2 Texto ditado

- O texto transcrito (no aparelho ou devolvido pela OpenRouter) é inserido no campo
  em que você está digitando ou numa nota do FlowVoice.
- **Revisão final por IA** (desligada por padrão): se você ligar, no fim do ditado o
  texto é enviado à OpenRouter para correção de pontuação, vírgulas, maiúsculas,
  acentos e concordância (modelo padrão `anthropic/claude-haiku-4.5`). Na nuvem vai o
  texto ditado; com o modelo no aparelho vai o texto da transcrição do áudio inteiro
  (ver 1.1).
- **Dicionário pessoal:** as correções de termos são aplicadas no próprio aparelho.

### 1.3 Chave de API da OpenRouter

- Você informa a sua chave. Ela é cifrada com AES-256-GCM, usando uma chave guardada
  no Android Keystore, e o resultado cifrado fica no armazenamento privado do app.
- A chave só é enviada à OpenRouter, no cabeçalho de autorização das chamadas de
  transcrição, revisão, validação da chave e lista de modelos.
- Ela não é registrada em log nem sincronizada, e fica fora do backup do Android.

### 1.3.1 Modelo de transcrição no aparelho (opcional)

- Se você escolher o modelo no aparelho, o app baixa uma única vez um arquivo de
  cerca de 475 MB das versões publicadas do projeto sherpa-onnx no GitHub
  (<https://github.com/k2-fsa/sherpa-onnx/releases>). Como em qualquer download, o
  GitHub recebe o endereço IP do aparelho nessa requisição; nenhum dado seu é
  enviado junto.
- O modelo fica no armazenamento privado do app, fora do backup do Android, e pode
  ser apagado em Ajustes a qualquer momento.
- Com ele, a transcrição ao vivo não usa a chave nem a internet. A revisão final por
  IA, se ligada, envia o áudio do ditado inteiro à OpenRouter no fim de cada ditado
  (ver 1.1).

### 1.4 Serviço de acessibilidade

O FlowVoice usa a API de Acessibilidade do Android **somente** para escrever o texto
ditado no campo ativo de outros apps, sem trocar o seu teclado. Com o serviço ligado,
o app:

- anota o **nome do pacote** do app em primeiro plano quando a janela muda, para
  voltar a ele depois do ditado; o conteúdo desses eventos não é lido;
- localiza o **campo de texto com foco** (posição na tela, tipo de campo, se é de
  senha, tamanho do texto) para posicionar a bolha e escolher onde escrever;
- lê o **trecho logo antes do cursor** para conferir e, se preciso, corrigir o que o
  próprio FlowVoice acabou de escrever; quando a inserção direta não está disponível,
  lê o texto do campo para acrescentar o ditado sem apagar o que já havia;
- **insere** o texto ditado no campo.

Não lê nem escreve em campos de senha (inclusive com "mostrar senha" ligado) nem nos
campos do próprio FlowVoice. Nada do que o serviço lê
é enviado para fora do aparelho, guardado ou usado para outro fim.

### 1.5 Sobreposição e notificações

A permissão de sobreposição mostra a bolha de ditado sobre outros apps. A de
notificações mostra o aviso do serviço da bolha. Nenhuma das duas coleta dados.

### 1.6 Notas, dicionário e ajustes

- Notas e termos do dicionário ficam no armazenamento privado do app e são
  **excluídos** do backup na nuvem e da transferência entre aparelhos.
- Ajustes do app (modelos escolhidos, opções ligadas ou desligadas) e a posição da
  bolha também ficam no aparelho, mas **podem** entrar no backup do Android, se ele
  estiver ativado na sua conta Google.

### 1.7 Login com Google (opcional)

- O login é opcional e só aparece se um ID de cliente Google for configurado em
  Ajustes. É feito pelo Credential Manager do Android e pelos serviços do Google.
- O app guarda **apenas o seu e-mail** localmente, fora do backup. Não há envio a
  servidor do FlowVoice: a sincronização existente não sai do aparelho.

### 1.8 Registros (logs)

- O app escreve registros técnicos no log do sistema Android (logcat), no aparelho:
  eventos, durações, contagens de caracteres, modelo usado e tipo de erro. O texto
  ditado não entra nesses registros nas versões publicadas; só uma versão de
  depuração com um marcador criado por `adb`, válido por 1 hora, o registra.
- A tela de Diagnóstico mostra um histórico recente que fica só na memória.
- O app não envia registros a ninguém.

## 2. Com quem os dados são compartilhados

- **OpenRouter, Inc.** (<https://openrouter.ai>) e o provedor do modelo que ela
  aciona recebem o áudio quando a transcrição é na nuvem ou quando a revisão final
  por IA está ligada (com o modelo no aparelho, no fim de cada ditado), e o texto se
  a revisão estiver ligada, vinculados
  à **sua** conta e chave da OpenRouter. O tratamento lá segue a política da
  OpenRouter: <https://openrouter.ai/privacy>.
- **GitHub**, apenas no download do modelo no aparelho: recebe o endereço IP da
  requisição, como em qualquer download.
- **Google**, apenas se você usar o Login com Google, conforme a Política de
  Privacidade do Google: <https://policies.google.com/privacy>.
- O desenvolvedor do FlowVoice não recebe, vende nem compartilha os seus dados. Não
  há anúncios, análise de uso nem rastreamento.

## 3. Segurança

Todo envio à OpenRouter usa HTTPS. A chave de API é cifrada no aparelho com uma chave
do Android Keystore. Os dados locais ficam no armazenamento privado do app.

## 4. Retenção e exclusão

- No aparelho: apague notas no app, ou limpe os dados do FlowVoice (Configurações →
  Apps → FlowVoice → Armazenamento) ou desinstale o app para apagar tudo o que ele
  guarda.
- Na OpenRouter e nos provedores: a retenção segue as políticas deles e as opções da
  sua conta OpenRouter.

## 5. Seus direitos (LGPD)

Você pode pedir confirmação de tratamento, acesso, correção, eliminação e demais
direitos da Lei 13.709/2018 pelo contato acima. Como o desenvolvedor não guarda os
seus dados, a maior parte desses direitos se exerce direto no aparelho ou junto à
OpenRouter.

## 6. Crianças

O FlowVoice não se destina a menores de 18 anos.

## 7. Alterações

Mudanças nesta política serão publicadas nesta página, com nova data de atualização.
