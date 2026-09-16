# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e [Versionamento Semântico](https://semver.org/lang/pt-BR/). As fases citadas
estão em [`docs/tasks/`](docs/tasks/README.md).

## [Unreleased]

## [0.5.0] - 2026-09-15

Bolha arrastável e inserção direta com prévia (P138 e P139) e janela de áudio
cortada na pausa da fala (P140). Desenho e decisões da bolha em
[`docs/tasks/P138-P139.md`](docs/tasks/P138-P139.md).

### Added

- P139: ditar pela bolha **digita cada trecho no campo aberto** assim que ele é
  transcrito, sem a barra nem o toque em Inserir. Os trechos entram em ordem, com
  espaço entre eles, e a palavra repetida na emenda sai do trecho novo (mesma regra
  da P127, que continua aberta). Tocar de novo na bolha encerra e digita o último
  trecho. Falha de trecho aparece na hora, e teto e chave recusada encerram como
  antes.
- P139: prévia junto à bolha, nos temas claro e escuro, com cronômetro, estado,
  **NO CAMPO** (o que já foi escrito), "transcrevendo…", **PENDENTE** e avisos.
  "Cancelar" descarta só o que não foi digitado. O resultado fica por 4 s
  ("Digitado no campo · N palavras"). O cartão fica numa janela própria, que
  **abre longe do cursor**: acima ou abaixo da linha do cursor em foco, sem cobri-la
  nem cobrir o teclado. Com pouco espaço, encolhe; sem espaço, some. A posição do
  cursor vem só das coordenadas do serviço de acessibilidade, sem ler o texto, e
  só é consultada com a prévia visível (ao abrir, quando o conteúdo muda e a cada
  1 s).
- P139: ajuste **"Revisar antes de inserir"** (desligado por padrão), que mantém o
  fluxo anterior: barra acima do teclado, Inserir e revisão por IA.
- P138: a bolha pode ser **arrastada** para qualquer ponto da área segura e encosta
  na borda mais próxima ao soltar. A posição (lado e fração da altura) fica salva
  fora da sincronização e volta ao mesmo lugar ao girar a tela e depois de reiniciar.
  Um toque curto aciona a bolha; um arraste nunca inicia ditado. Com TalkBack, a
  bolha tem nome, papel de botão e as ações "Mover para cima", "Mover para baixo" e
  "Mover para o outro lado". A posição padrão (direita, 30 % da altura) sai de
  cima da tecla de ação e do microfone das Notas (P27, P123).
- P138: a bolha é **totalmente redonda**. A sombra e o anel pulsante passavam da
  folga da janela retangular e eram cortados nas bordas dela, o que formava um halo
  quadrado. Agora são recortados em círculo, com sombra menor.
- P141: a bolha **volta sozinha depois de atualizar o app**. Reinstalar mata o
  processo e leva a bolha junto, e antes era preciso religá-la em Ajustes. O estado
  "ligada" agora fica guardado no aparelho, fora da sincronização, e o app religa a
  bolha ao voltar ao primeiro plano, se as permissões continuarem valendo.

### Changed

- P147: a revisão por IA passa a valer também na digitação direta, **uma vez só,
  no fim do ditado** (a P139 a dispensava por completo aqui). Revisar trecho a
  trecho dobraria custo e latência sem o contexto da frase; no fim, a frase
  inteira existe e é ela que vai ao modelo. Ela continua ao revisar antes de
  inserir e nas notas.
- P139: o microfone do Início também digita direto, só no app que ele reabre
  (P130).
- P140: a janela de áudio é **cortada na pausa natural da fala**, e não mais a
  cada ~4 s. Ela sai com 300 ms de pausa depois de ao menos 1,2 s de áudio e
  400 ms de fala, cortada no meio da pausa. Falando sem pausa, continua o teto de
  ~4 s. O limiar de pausa acompanha o ruído de fundo da sessão, e silêncio ou
  ruído sem fala só saem no teto, sem pedidos a mais. Com as janelas fixas, no S26
  (`gpt-transcribe`, ditado de 23 s), os pedidos saíam a cada 3,4–4,3 s, a
  OpenRouter respondia em 1,1–1,7 s e o primeiro texto entrou 6,1 s depois do
  início. A meta é o texto ~1,5–2 s depois de cada frase, ainda não medida.
- P140: a captura lê frames de 100 ms (eram 256 ms no S26), o que dá resolução
  ao corte na pausa.
- P140: o teto de pedidos por sessão passou de 30 para 90 (~3 min de fala com as
  janelas menores). Uma captura muda ainda para, em ~6 min. O custo máximo por
  sessão com o `gpt-transcribe` é de ~US$ 0,03.
- P140: `dictation_window` no log traz `cut` (`pause`, `leading`, `ceiling`,
  `flush`) e `noiseFloor`.
- P143: cada janela é transcrita **com o último 1 s da janela anterior** à
  frente. Antes, cada janela ia sozinha, e quanto menor a janela, menos contexto
  o modelo tinha: no S26 (`gpt-transcribe`) "diarreia" partida entre janelas saiu
  como "arreio" e um nome próprio virou "Grandmont". A API de transcrição da
  OpenRouter não aceita `prompt`, então o contexto só pode ser dado em áudio. O
  contexto não entra na linha do tempo nem na contagem de bytes, e uma janela
  muda continua sendo pulada sem custo (P124).
- P143: a palavra repetida na emenda passa a ser reconhecida mesmo quando o
  modelo a escreve com outro caixa, outra pontuação ou outro acento, e a palavra
  partida no corte é fechada sem espaço ("…de di" + "de diarreia" → "diarreia").
  Palavra que o usuário repetiu de propósito sobrevive quando o contexto a
  ancora.
- P143: a janela mínima passou de 1,2 s para 2 s e a pausa mínima de 300 ms para
  450 ms, para uma hesitação no meio da frase não partir a frase. Custa ~150 ms a
  mais depois de cada frase e até ~800 ms na primeira janela de uma frase curta.
- P143: o áudio enviado por minuto de ditado sobe ~40 % (~US$ 0,0018 por
  minuto); o teto de 90 pedidos por sessão não mudou e agora cobre ~3,7 min de
  fala. `transcription_request` ganhou `contextMs`.
- P144: **janela sem fala não é mais transcrita**. Depois da P143, uma janela
  cortada antes da fala (ou o resto do fim) podia conter só silêncio mais o
  contexto, e o modelo devolvia **o contexto** como texto novo, que entrava no
  campo ("3Gs.", "Tô cansado."). Agora a fala de cada janela é medida só no áudio
  dela, com o limiar que acompanha o ruído da sessão, e a janela sem fala vira
  trecho vazio sem gastar requisição. Fala baixa continua indo à transcrição: só
  os cortes que por construção não esperam fala são pulados.
- P144: **o ponto não fica mais no meio da frase**. O modelo fecha cada trecho
  com ponto; quando o trecho seguinte continuava a frase, saía "O exame de
  sangue. mostrou leucocitose.". Agora esse ponto é apagado antes de escrever o
  trecho novo. Só vale para ponto que o próprio FlowVoice escreveu e que ainda
  está logo antes do cursor: depois de "Inserir aqui" ou de qualquer recusa, nada
  é apagado. Trecho que começa com maiúscula é tratado como frase nova e mantém o
  ponto.
- P144: `transcription_silent_window` ganhou `reason` e `cut`;
  `dictation_direct_inserted` ganhou `erased`.
- P146: a repetição do contexto sobreposto passa a ser cortada **pelo tempo**, e
  não por comparação de texto. Quando o modelo transcrevia o contexto de outro
  jeito, a repetição não era reconhecida e o trecho entrava dobrado: no S26,
  "Avaliado pelo doutor." seguido de "Segundo doutor Grandmont." deixou no campo
  "Avaliado pelo doutor. Segundo doutor Grandmont.". Agora a janela que leva
  contexto pede os tempos por palavra e tudo o que termina antes do fim do
  contexto é descartado, mesmo com outras palavras.
- P146: **o ditado não para se o provedor não der tempos**. Sem `words`, o corte
  é por segmento; sem tempo nenhum, ou se o provedor recusar o formato, a janela
  é refeita no formato de antes e vale a comparação por texto. A recusa fica
  lembrada por modelo, então acontece uma vez, não a cada janela
  (`transcription_verbose_unsupported`).
- P146: o áudio enviado não muda e o custo por minuto de ditado continua o da
  P143; só a resposta fica maior (os tempos de ~20 palavras por janela). A folga
  do corte é de 120 ms e anda para trás: na dúvida a palavra fica, e a
  deduplicação por texto a remove. `transcription_context_trimmed` traz quanto
  foi cortado, sem o texto.
- P147: **a pontuação do ditado direto é revista no fim.** O modelo de
  transcrição só vê uma janela de 2 a 4 s por vez e pontua cada uma como se fosse
  a frase inteira: no S26 (2026-09-16 09:29) saíram "Hoje o dia está muito
  bonito.", "Por isso iremos para a praia." e "Para a praia pela manhã.", com
  ponto final cedo demais e sem vírgula. Terminado o ditado (toque na bolha ou
  teto), o texto inteiro que o FlowVoice escreveu vai ao modelo de revisão
  (`openai/gpt-4o-mini`), que só pode mexer em pontuação, maiúsculas, acentos e
  ortografia, e volta trocado no campo de uma vez: apaga exatamente os caracteres
  que o app inseriu e escreve a versão revisada.
- P147: **qualquer falha deixa o campo como está.** Guard recusando a resposta,
  rede fora do ar, chave inválida, trecho ainda pendente, campo trocado no meio
  ou ditado acima de 4000 caracteres: nada é apagado. A troca só acontece com o
  que o app escreveu ainda imediatamente antes do cursor, no mesmo campo (as
  travas da P139 e da P144), e a contiguidade é conferida **de novo depois da
  resposta**, porque o campo pode ter mudado no ~1 s da chamada. Revisão idêntica
  ao ditado não apaga nem escreve nada.
- P147: a prévia mostra **"revisando…"** enquanto isso, e o resultado depois. O
  custo é de uma chamada a mais por ditado (~US$ 0,0001 num ditado de 300
  caracteres com o `gpt-4o-mini`) e o fim do ditado atrasa ~1 s; o `latencyMs` do
  `dictation_finalized` passa a incluir essa espera.
- P147: log `dictation_proofread_applied chars= erased= drift=` e
  `dictation_proofread_skipped reason=` (`desligado`, `pendente`, `vazio`,
  `nao_contiguo`, `muito_longo`, `guard`, `sem_mudanca`, `erro`, `recusado`,
  `campo_diferente`), sem texto; o texto continua só no log da P135
  (`proofreading_input` e `proofreading_output`).
- P148: **a troca apaga o que está no campo, não o que o app anotou.** No S26
  (2026-09-16 10:29) a pontuação saiu certa, mas sobrou a primeira letra do
  ditado — "HHoje o dia..." —, porque o campo tinha um caractere a mais do que a
  conta do app. Agora a revisão lê o texto que está de fato antes do cursor
  (`getSurroundingText`, API 33+) e o casa com o ditado letra a letra, ignorando
  espaços e pontuação, que é justamente o que diverge; os sinais que sobram no
  meio entram no que será apagado. Letra diferente no meio (alguém digitou junto)
  ou diferença acima de 16 caracteres para mais ou para menos **não apagam nada**
  (`reason=campo_diferente`), e quem não souber ler o campo continua com a conta
  do app. O `drift=` do log mede essa diferença: `drift=0` é o esperado.

### Security

- P139: a digitação sem toque só acontece com destino conhecido, no mesmo app e,
  depois do primeiro trecho, no mesmo campo. O serviço de acessibilidade conta
  início e fim de input, e trocar de conversa ou de campo pausa. Qualquer recusa
  pausa a sessão até o fim, mesmo que o foco volte ao app de origem, e só "Inserir
  aqui" escreve no app atual. Isso cobre, no modo direto, a conferência por pacote
  da P85 e o sucesso falso sem campo da P114. Continuam as travas de senha, do
  próprio FlowVoice e da P113 (toque até 1 s depois da recusa é ignorado).

### Known issues

- Validado no S26 sem fala: arraste, encaixe, posição lembrada depois de reiniciar
  o app e de girar a tela, toque curto, prévia e cancelamento. A digitação com fala
  ainda não foi testada.
- A linha do cursor vem de `EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY`. Se o app não
  a informa, a prévia evita o campo inteiro. Se o campo é grande (mais de 40 % da
  faixa útil) e a linha é desconhecida, vale a regra antiga pela metade da tela da
  bolha, que pode cobrir o cursor (ver `docs/tasks/P138-P139.md`).
- Sem campo em foco, o cartão aberto pela regra antiga pode ficar sobre a linha
  que se quer tocar e receber o toque. Focar o campo antes de ditar evita isso.
- Um app que reinicie o input a cada `commitText` pausaria a digitação a cada
  trecho (`dictation_direct_paused route=trava:campo`). Não foi medido.
- P140: os limiares do corte na pausa foram escolhidos sem fala real (pausa até
  2× o piso de ruído, mínimo 100; fala acima de 3×, mínimo 250) e não foram
  medidos no S26. Uma hesitação de 300 ms no meio da frase parte a janela, e o
  modelo pode fechar o trecho com ponto e abrir o seguinte com maiúscula. A
  transcrição continua uma de cada vez: uma janela que sai com a anterior ainda na
  OpenRouter espera por ela.

## [0.4.8] - 2026-09-15

### Changed

- P137: a transcrição agora envia `temperature: 0`. Nem a OpenRouter nem a
  OpenAI documentam o valor padrão, e 0 é o mais determinístico, então o
  resultado deixa de depender do padrão de cada provedor.

## [0.4.7] - 2026-09-15

### Changed

- P136: o modelo de transcrição padrão passou de `openai/gpt-4o-mini-transcribe`
  para `openai/gpt-transcribe`. No teste com fala no S26, o modelo antigo trocou
  "ditado" em 2 de 3 frases curtas ("Primeiro digitando", "Terceirizado"). O
  erro estava na transcrição de uma janela só, não no corte nem na revisão. No
  Benchmark F05 de 2026-09-15 (17 palavras), `gpt-transcribe` teve WER 0 e o
  antigo errou uma palavra; na rodada de 2026-09-13, os dois tiveram WER 0. O
  custo é ~3× maior (cerca de US$ 0,0011 por 15 s de áudio). A evidência ainda é
  de dois clipes curtos (ver `docs/PLAN.md`).

## [0.4.6] - 2026-09-14

Correções dos achados P130 a P135, do primeiro teste com fala no S26 (três
ditados no Samsung Notes) e da revisão de código feita em paralelo.

### Fixed

- P130: o microfone do Início não voltava ao app em que você estava. Ele só
  mandava o FlowVoice para trás, e no One UI o que aparece embaixo é o
  launcher, mesmo quando se chega pelos recentes. Agora, a cada troca de
  janela, o serviço de acessibilidade anota o app cuja janela de aplicação
  está ativa, conferindo a lista de janelas do sistema, sem consultar o app em
  primeiro plano. Não contam bolhas de outros apps, teclado, cortina, o próprio
  FlowVoice, o launcher, pacotes sem ícone nem apps que o FlowVoice abriu
  (Ajustes, compartilhamento) até você passar pelo launcher. Ao começar a
  gravar, o Início reabre esse app. Isso revê em parte a SEG-5: o serviço passa
  a assinar `typeWindowStateChanged` e lê só o pacote e a janela do evento.
- P131: o áudio era cortado exatamente a cada 4 s, podendo partir uma palavra
  entre duas janelas transcritas separadamente ("Terceiro ditado" saiu
  "Terceiro colocado"). O corte agora cai no trecho de 120 ms com menor
  energia média entre 3,1 s e 4,3 s, de modo que a pausa curta de uma
  consoante no meio da palavra não conta como pausa. A janela sai até 0,3 s
  depois do alvo, e início e fim de cada janela vêm dos bytes, sem deriva.
- P132: a revisão por IA reescrevia o ditado (`Primeiro ditado: "Pelo
  início."`). O texto agora vai entre `<ditado>` e `</ditado>`, com um prompt
  que proíbe responder, trocar palavras e acrescentar citação. A revisão é
  descartada (`proofreading_rejected`), e o texto transcrito é mantido, quando
  muda a quantidade de palavras, um número ou uma palavra curta, troca uma
  palavra longa por outra com mais de 2 letras de diferença, ou acrescenta
  aspas, dois-pontos, quebra de linha ou a marca `<ditado>`.
- P133: depois do aviso "O microfone não respondeu a tempo.", a sessão pedida
  podia continuar gravando se o microfone abrisse tarde. Agora o próprio
  pipeline a cancela, mesmo que a tela gire ou você saia do Início. Um novo
  toque no microfone não é cancelado pela espera anterior, e o aviso diz que o
  ditado foi cancelado.
- P134: a chave OpenRouter era lida do cofre a cada janela, e uma falha
  passageira do Keystore encerrava o ditado como "chave ausente". Agora ela é
  lida uma vez por sessão.
- P135: o logcat não trazia status HTTP nem novas tentativas da transcrição, e
  nenhum texto que permitisse separar erro de transcrição de erro de revisão.
  Agora `transcription_error` traz `status`, cada nova tentativa gera
  `transcription_retry` e `proofreading_applied` traz `inputChars`. O texto
  ditado (`transcription_window_text`, `proofreading_input` e
  `proofreading_output`) só vai ao logcat em build de depuração **e** com o
  marcador criado por
  `adb shell run-as dev.rafaelbrauner.flowvoice touch files/transcript-text-logging`
  há menos de 1 h. Nunca aparece na tela de Diagnóstico, e linhas longas saem
  em partes numeradas.

## [0.4.5] - 2026-09-14

Correções do pipeline de voz feitas sem o aparelho: achados P124 e P125 da
revisão do pipeline, e parte da P121 e da P122.

### Fixed

- P124: janelas de silêncio digital (PCM zerado) eram enviadas à OpenRouter,
  custando uma requisição cada e podendo voltar como texto inventado. É o que
  o sistema entrega quando silencia a captura, por exemplo num ditado de nota
  com o app em segundo plano (P121). Essas janelas agora viram trecho vazio,
  sem requisição, e continuam contando no teto da sessão, para que uma captura
  muda ainda termine. Só silêncio digital é filtrado; fala baixa e ruído de
  sala continuam sendo enviados.
- P125: HTTP 408 da OpenRouter ("request timed out") virava "resposta
  inválida", sem nova tentativa; agora é tratado como timeout e repetido.

Testes novos cobrem a parada por chave recusada ou por teto enquanto a captura
ainda está iniciando, o coordenador de nota com status igual ao anterior e o
teto contado na submissão com valor exato (P122). Coberto por testes unitários;
nada disto foi medido no aparelho, inclusive se a captura em segundo plano
chega zerada no S26 (ver P94).

## [0.4.4] - 2026-09-14

Correções dos achados P110 a P113 do `/verificar` da 0.4.3.

### Fixed

- P110: depois de uma segunda tentativa de ditar numa nota sem chave, a nota
  ficava armada e o texto da sessão seguinte, ditado em outro app, também ia
  para ela; o Início mostrava "O microfone não respondeu a tempo." e a barra
  ficava sem liberação. Cada sessão do pipeline passa a ter identidade
  (`session`), e Notas, Início e barra acompanham a sessão em vez de comparar
  status. Parar numa sessão de campo ativo leva à revisão e não insere sem
  toque em Inserir.
- P111: chave rejeitada no meio da gravação descartava todo o texto já
  transcrito. Agora a captura para e o texto é entregue com aviso: na barra,
  para revisar; na nota, anexado. A revisão por IA não é chamada com a chave
  recusada.
- P112: HTTP 403 (guardrail, moderação ou permissão, segundo a documentação da
  OpenRouter) era tratado como chave inválida e encerrava a sessão; agora só o
  trecho falha, com o motivo "recusado pela OpenRouter".
- P113: depois de uma recusa porque o foco mudou de app, o próximo toque em
  Inserir escreve no app atual, mas o aviso dizia só "texto mantido na barra", e
  num toque duplo o segundo toque inseria sem o usuário ver a recusa. O aviso
  agora diz "toque em Inserir de novo para inserir no app atual", e um toque até
  1 s depois da recusa é ignorado.

Coberto por testes unitários; ainda não validado no aparelho (ver P94).

## [0.4.3] - 2026-09-14

Correções dos achados do `/verificar` da 0.4.2 e do `/debugar` de P95 e P106.

### Fixed

- P99: texto de nota ditada podia ser digitado no app em foco sem ação do usuário
  (parada pelo teto); cada sessão agora tem destino explícito (`DictationTarget`)
  e sessão de nota nunca chama a inserção.
- P100: ditado de nota assumido pela bolha travava em "Ready"; a barra oferece
  Parar e Cancelar (sem Inserir) e o texto vai para a nota.
- P101: depois do teto, a recusa de inserção podia ficar permanente; o destino é
  capturado no toque em Inserir e recapturado a cada nova tentativa.
- P102: girar a tela durante um ditado de nota selecionava outra nota e escondia
  o botão de parar.
- P103: ocultar o botão flutuante cancelava ditado de nota; só cancela sessão de
  campo ativo iniciada ou assumida pelo overlay.
- P107: sem chave OpenRouter o microfone ficava aberto indefinidamente; chave
  ausente ou rejeitada encerra a sessão, e o teto passa a contar no envio.
- P108: a conferência de destino podia usar o pacote do último editor; o pacote
  em foco vem da janela ativa.
- P109: campos editáveis anunciavam o rótulo no lugar do texto; o rótulo
  acessível só aparece com o campo vazio, e os campos de Ajustes ganharam nome.

## [0.4.2] - 2026-09-13

Correções dos achados do `/verificar` da 0.4.1 e do teste real no S26.

### Fixed

- P67: ditado de nota não fica mais órfão ao trocar de aba ou girar a tela; um
  coordenador de escopo de app anexa o texto à nota e as Notas voltam a mostrar
  o botão de parar.
- P91: sessão iniciada pelo microfone do Início mostrava só a bolha, gravando
  sem controle; a barra aparece e tocar na bolha durante uma sessão a assume.
- P92: o teto de requisições por sessão encerra a captura e entrega o texto até
  ali, com aviso, em vez de gravar indefinidamente.
- P90: telas do sistema (acessibilidade, sobreposição, detalhes do app,
  compartilhar) abriam na tarefa do FlowVoice, e o ícone reabria Configurações.
- P80: inserção recusada mantém o texto na barra para tentar de novo.
- P83: o destino da inserção é conferido também no ditado iniciado pelo Início.
- P81: o cache de acessibilidade é limpo antes de ler janelas e o campo focado
  (barra acompanha o teclado; fallback não usa texto antigo).
- P69: nomes para o TalkBack em toggles e campos.

### Added

- P66: o onboarding e o README orientam a liberar "configurações restritas" em
  instalações fora de loja (Android 13+) e a não usar o FlowVoice como atalho de
  acessibilidade.
- P84: o CI roda os testes unitários do app Android.

## [0.4.1] - 2026-09-13

Correções dos achados da auditoria de segurança da 0.3.1 (SEG-1 a SEG-7).

### Security

- SEG-6: a chave OpenRouter passa a ser cifrada direto no Android Keystore
  (AES-256-GCM, cofre `flowvoice_vault`), sem o fallback do Tink que gravava o
  keyset em claro; a chave do cofre antigo é migrada na primeira abertura e o
  arquivo antigo só é apagado depois de a chave ser confirmada no cofre novo.
- SEG-1: notas, dicionário, conta e os cofres saem do backup em nuvem, da
  transferência entre aparelhos e do backup completo.
- SEG-7: a rota direta recusa campo de senha, e a inserção é recusada se o app em
  foco mudou desde o início do ditado.
- SEG-5: o serviço de acessibilidade não assina mais nenhum tipo de evento.
- SEG-4: o diagnóstico do campo focado não devolve conteúdo, e o caminho de
  depuração saiu do código principal.
- SEG-3: o log não registra mais o pacote do app em que se dita.
- SEG-2: apagados os APKs debug antigos publicados pelo CI a partir da `main`.

## [0.4.0] - 2026-09-13

Redesign do app a partir do handoff de design (F12 UX).

### Added

- Sistema visual: tema escuro (principal) e claro, Instrument Sans e JetBrains
  Mono empacotadas (SIL OFL 1.1), ícones vetoriais e componentes (pílula de
  status, botão de microfone com pulso, waveform, texto provisório pontilhado,
  toggle, cartões, barra de navegação).
- Telas novas: login, onboarding com as três permissões reais, Início (estado do
  serviço, microfone, últimas notas), Notas em mestre-detalhe com painel
  colapsável e ditado na nota, Dicionário, Ajustes e Diagnóstico (log do
  serviço, exportar relatório e benchmark F05).
- Barra de ditado ao vivo (variação 1b) no botão flutuante: texto finalizado e
  provisório, cronômetro, Cancelar e Inserir, ancorada acima do teclado.
- Pipeline com revisão antes de inserir (`finalizeForReview` → `Ready` →
  `insertReady`), sem mudar o `finalize` usado pelas Notas.

### Changed

- A `MainActivity` virou só hospedeiro da navegação; o microfone do Início abre
  a barra de ditado no app anterior.
- A chave salva só aparece mascarada (`sk-or-v1-••••` + 4 últimos caracteres).
- Login opcional enquanto não há backend (P07).
- O serviço de acessibilidade passa a ler os limites da janela do teclado
  (`flagRetrieveInteractiveWindows`) para posicionar a barra.

### Removed

- Tela POC antiga da `MainActivity`; as funções dela foram para Ajustes e
  Diagnóstico.

## [0.3.1] - 2026-09-13

Correções dos achados da verificação de `9d527d7` (ver `TAREFAS_PENDENTES.md`).

### Fixed

- P30: o fallback `ACTION_SET_TEXT` apagava o conteúdo do campo do app-alvo;
  agora insere no cursor e recusa em campo de senha ou texto ilegível.
- P31: trecho com falha sumia do texto sem aviso; o texto parcial é inserido com
  aviso do trecho e do motivo, e sem nenhum trecho transcrito o motivo real aparece.
- P32: toque duplo ou cancelar durante o início do ditado geravam erro ou eram
  ignorados (novo estado `Starting`).
- P33: ditado pela tela do FlowVoice podia cair nos próprios campos do app (ex.:
  Google Web Client ID) e ser salvo nas preferências.
- P45: o runtime empacotado do desktop não tinha `java.net.http`, e a chave nunca
  validava no app instalado.
- P46: no desktop, iniciar ou cancelar durante a transcrição final apagava ou
  misturava o texto de outra sessão.
- P47 e P41: falha de captura depois do início (desktop e Android) deixava a
  sessão "gravando" sem áudio.
- P36: com a chave-mestra do Keystore inutilizável o app caía em loop ao abrir, e
  uma falha passageira apagava a chave salva; o cofre agora só é recriado em
  corrupção real e, fora isso, fica "indisponível" sem derrubar o app.
- P37: o botão flutuante derrubava o app quando a sobreposição falhava
  (permissão revogada ou Android 7); a permissão é conferida de novo e a falha é
  tratada.

### Changed

- `AudioCaptureEngine` avisa erro durante a captura (`start(onFrame, onError)`,
  compatível com as implementações existentes).
- F12.4 volta para "em andamento" até o aceite com fala real (P34).

### Added

- Testes de regressão do pipeline: sessões seguidas, inserção falha, erro de
  captura e frames de outra thread (P35).
- CI confere que o runtime do desktop inclui `java.net.http` e roda os testes do
  `desktopApp`.

## [0.3.0] - 2026-09-13

### Added

- F12.4: o botão flutuante dita no app aberto. Um toque grava, outro finaliza e
  insere no campo focado, e um toque longo cancela. A captura roda num foreground
  service de microfone iniciado com a Activity visível.
- `DictationPipeline` no `shared`: sessão, transcrição, prévia, dicionário,
  revisão e inserção, usado pela tela principal e pelo botão flutuante.
- F13 (preparação para Windows): contrato `TextInserter`, captura via
  `javax.sound`, inserção via `SendInput` (JNA), chave cifrada com DPAPI, módulo
  Koin do desktop e app Compose Desktop mínimo (Compose Multiplatform 1.8.2).
- CI roda `lintDebug` e compila o app desktop.
- `README.md` com build, instalação e configuração; `CHANGELOG.md`,
  `TAREFAS_PENDENTES.md` e `MELHORIAS.md`.

### Changed

- Um único contrato `TextInserter` (`shared.insertion`) para Android, pipeline e
  desktop.
- `AGENTS.md`, F01, F02 e PLAN alinhados ao código (compileSdk 35; inserção
  direta exige API 33).
- Versão 0.3.0 no app Android, no `shared` e no desktop.

### Fixed

- `commitText` deixava o cursor antes do texto; trechos seguidos entravam
  invertidos.
- O texto final perdia o último trecho da fala: a última janela chegava à fila
  depois do `awaitIdle` (Android e desktop).
- `ModelInfo.contextLength` vinha sempre nulo (faltava `context_length`).
- O backup restaurava o cofre da chave sem a chave do Keystore; o cofre ilegível
  agora é recriado vazio.

### Security

- O receiver de depuração por adb só existe no build debug e exige
  `android.permission.DUMP`; antes qualquer app podia mandar o FlowVoice digitar
  no campo focado.

### Removed

- Guarda de API morta no fallback `ACTION_SET_TEXT`.

## [0.2.0] - 2026-09-13

Fases F03 a F12 (PR #2, branch `f03-audio-capture-session`).

### Added

- F03: captura contínua de áudio (16 kHz, PCM 16-bit mono), sessão de ditado
  com janelas de ~4 s e detecção de silêncio.
- F04: transcrição incremental via OpenRouter (`/audio/transcriptions`), chave
  guardada cifrada, validação da chave, retry com backoff, `Retry-After` e teto
  de requisições por sessão.
- F05: benchmark de modelos com WER, latência e custo sob teto de gasto.
  Padrão `openai/gpt-4o-mini-transcribe`, fallback `deepgram/nova-3`.
- F06: prévia ao vivo com trechos provisórios e finalizados; inserção única no
  finalize.
- F07: dicionário pessoal com sugestões, aprovação e aplicação na prévia.
- F08: notas locais (criar, editar, apagar).
- F09: revisão opcional de pontuação e ortografia via OpenRouter.
- F10: login Google via Credential Manager.
- F11: `SyncEngine` offline-first para notas, dicionário e preferências (remoto
  ainda em memória).
- F12: botão flutuante, diagnóstico exportável e integração das fases no app.

### Known issues

- Login sem validação do ID token em backend; sync sem servidor real.
- O botão flutuante abre o app e tira o foco do campo do app-alvo (corrigido na
  0.3.0).

## [0.1.0] - 2026-09-13

Fases F00 a F02.

### Added

- F00: plano, requisitos, `AGENTS.md`, atribuições e referências.
- F01: scaffold Kotlin Multiplatform (Android + desktop) com Ktor, Koin e CI.
- F02: POC de inserção direta no campo focado via serviço de acessibilidade
  (`commitText`, fallback `ACTION_SET_TEXT`), validada no Galaxy S26 Ultra.
