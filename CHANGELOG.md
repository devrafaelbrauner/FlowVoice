# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e [Versionamento Semântico](https://semver.org/lang/pt-BR/). As fases citadas
estão em [`docs/tasks/`](docs/tasks/README.md).

## [Unreleased]

- **Bolha parada vira um ponto**: sem ditado em andamento, o botão flutuante é um ponto laranja de 16 dp (janela de
  36 dp, antes 76 dp); ao tocar, volta à bolha inteira com o microfone enquanto dita e revisa.

### Corrigido (ditado na nuvem ao vivo, 2026-09-28)

- **Frase repetida depois de uma pausa sumia do campo** (S26, qwen/qwen3-asr-1.7b): a emenda entre
  janelas (`TranscriptOverlap`) tirava do começo da janela o que repetia o fim do texto mesmo sem áudio
  repetido, ou depois de o corte pelo tempo já ter tirado o contexto — "Amanhã de manhã vamos à praia." (30
  caracteres, o que 1 s de contexto autoriza) voltou três vezes da nuvem e entrou uma. Agora sem contexto
  nada é removido (P127), o contexto só vai quando o fim da janela anterior tem fala, e o corte pelo tempo
  diz quanto do contexto sobrou (`contextInTextMs`, log `contextLeftMs`).
- **Uma janela lenta segurava as seguintes:** as janelas iam à nuvem uma de cada vez; no S26 a janela 1
  do nova-3 ficou presa e nada mais entrou até parar (1ª inserção aos 20 s). Agora até 3 em voo, e a
  janela presa não impede que as seguintes entrem no prazo do fim.
- **Tocar enquanto a revisão final está a caminho descartava o texto final** (S26 13:56: passada final de
  150 caracteres descartada, ficou o rascunho de 88). O cartão "revisando…" não tem mais "Cancelar", o
  cancelar da sessão direta depois do toque de parar é ignorado (`dictation_cancel_ignored`) e a bolha diz
  "Revisando o ditado".
- **Emenda e ordem no Samsung Notes** ("bem?Consegue", "tarde?\nPaciente", "coco?\n tomar", e "bem?
  Paciente do leito doze.Consegue me ligar…", que fazia a passada final ser recusada com `campo_diferente`):
  sondas de texto fixo no S26, sem nuvem, mostraram que o corpo de nota trata um `commitText` de 25
  caracteres ou mais como colagem — aplica de ~60 a ~300 ms depois, tira o espaço da frente e do fim, põe
  uma linha nova no fim e deixa o cursor depois dela, ignorando o `newCursorPosition` —, enquanto até 24
  entra na hora e igual ao mandado (o título da nota, um EditText, aceita os longos). O trecho seguinte ia
  para a linha de baixo, ou, se saía antes de o longo ser aplicado, entrava antes dele. Agora cada escrita
  vai em pedaços de até 20 caracteres, cortados antes de um espaço (`CommitChunks`), com as mesmas travas.
  No S26, 6 modelos × 2 ditados: nenhuma linha nova, frase colada ou recusa `campo_diferente`, no ao vivo e
  no final, e `drift=0` sem linha nova no fim em todas as trocas (antes `drift=1` em 5 de 11 e linha nova no
  fim em todas). O fim do ditado segue conferindo espaço e linha nova (`dictation_field_restored`) como rede.

- **Todas as janelas ao vivo falharam e a passada final nem era tentada** (qwen sem resposta no S26): o
  ditado acabava em "Nenhum trecho transcrito". Com trecho falho a passada final vai mesmo sem rascunho, e o
  texto final entra no campo; áudio sem fala continua sem pedido.

### Alterado

- **Janela ao vivo da nuvem de ~2 s** (`SpeechEndpointing.LIVE`: alvo 2 s, pausa de 250 ms depois de 1 s),
  no lugar de 4 s / 2 s / 450 ms: na réplica com 6 modelos a 1ª inserção caiu de 3,8–6,3 s para 1,4–3,5 s
  depois da fala, com 10–13 inserções por ditado de ~22 s em vez de 6–8; números do S26 em
  `TAREFAS_PENDENTES.md` (Nuvem ao vivo). Teto de janelas por sessão 90 → 180 (~6 min, como antes).
- **Cartões:** saem "Nada transcrito." e "Digitado no campo · N palavras". Trecho falho só avisa quando o texto
  que ficou é o ao vivo (revisão desligada, ou ela não veio e havia trecho falho); o aviso "Versão final da
  nuvem não veio" sozinho sai, porque sem trecho falho nada faltou. O aviso é uma linha curta, sem cabeçalho
  nem botões, que some em ~4 s ou com um toque.

- **Padrão da passada final: só a transcrição do áudio inteiro, sem formatação**
  (`openai/gpt-4o-mini-transcribe`), por escolha do usuário depois da medição: 0 erros que mudam sentido,
  nota 95,8, 1,9 s no S26 e texto igual ao gabarito. O passo único com `thinkingmachines/inkling` (nota 97,7)
  segue em Ajustes, mas deu 7,4 s e erro de servidor no S26. Custo estimado da passada final: US$ 0,0018/min.

### Adicionado

- **Motor Nuvem completo, com os modelos escolhidos.** Com a "Revisão final por IA" ligada, o motor Nuvem
  segue digitando ao vivo as janelas curtas pelo modelo de transcrição escolhido e, ao parar, reenvia o
  **áudio inteiro** à OpenRouter pela mesma passada final do motor do aparelho (`FinalPass`: pedaços de até
  50 s cortados em silêncio, guarda, teto de 8 s, fallback) e troca o que foi digitado pelo caminho da
  revisão final. Falhou, demorou ou foi recusado: fica o texto ao vivo, com o aviso "Versão final da nuvem
  não veio: ficou o texto ao vivo". A revisão só de texto da nuvem (DP-nuvem) sai.
- **"Formatação" em Ajustes** (novo, vale para a passada final dos dois motores): **um passo só** (o
  padrão), em que um modelo de chat ouve o áudio e devolve o texto já formatado (`OpenRouterAudioChatClient`,
  `thinkingmachines/inkling`, teto de 12 s), um modelo de chat depois da transcrição (`openai/gpt-4.1`,
  conferido pela guarda) ou **sem formatação** (fica a transcrição do áudio inteiro). Sem transcrição para
  conferir, o passo único só troca o rascunho se o texto tiver ao menos 35 % das palavras dele
  (`FinalPass.draftOverlap`, log `final_pass_skipped reason=diverge`): na medição o `gpt-audio-mini`
  respondeu "Claro, vou avisar assim que chegar." a "Me avisa quando chegar, por favor.". Os seletores de
  transcrição e de formatação listam primeiro os modelos medidos, com a nota e o tempo de um ditado de
  20 s (`CloudModels`); o pedido leva o raciocínio mínimo medido de cada modelo (`reasoning`). O log
  `final_pass_done` ganhou `mode` (`dois_passos`, `so_transcricao`, `um_passo`), `model` e
  `formattingModel`.
- **Medição dos modelos da nuvem** (`docs/medicao-modelos-nuvem.md`, US$ 1,81): as 24 transcrições da
  OpenRouter, 23 modelos de chat com áudio em passo único e 15 formatações sobre as duas melhores
  transcrições, com erro de palavra sensível a acento, cedilha, hífen e caixa de nome próprio, trocas só de
  acento, F1 de vírgula, fim de frase, "?" e ":", latência em série e custo; gabaritos com erro conhecido
  corrigidos à parte (`tools/medicao/gabaritos-corrigidos.txt`).

- **Duas passadas no motor do aparelho.** Com a "Revisão final por IA" ligada (o antigo "Revisão por
  IA", mesma chave, sem ajuste novo), o Nemotron segue digitando o rascunho ao vivo e, ao parar, o
  **áudio inteiro** do ditado vai uma vez à OpenRouter: transcrição (`openai/gpt-transcribe`, o modelo
  da nuvem escolhido em Ajustes) → pontuação falada no texto inteiro → formatação (`anthropic/claude-haiku-4.5`)
  → guarda contra a transcrição da nuvem → vocabulário do usuário → troca do rascunho pelo caminho da
  revisão final (mesmas travas: sem pendente, contíguo, nunca depois de "Inserir aqui" noutro campo,
  até 4000 caracteres, apagando só o que o campo confirma). Nota e barra de revisão recebem o texto
  final. O áudio é o das janelas que o pipeline já guarda em memória (nunca em disco; cancelar o
  descarta); acima de 50 s vai em pedaços cortados em fim de janela em silêncio, transcritos em
  paralelo e emendados na ordem. Teto de 8 s (`FinalPass.TIMEOUT`). Sem chave, sem rede, erro, prazo
  ou transcrição com menos de 60 % das palavras do rascunho: fica o rascunho, e o cartão avisa "Versão
  final da nuvem não veio: ficou o texto do aparelho". Se só a formatação falhar ou for recusada, vale
  a transcrição da nuvem como veio. Logs só com contagens e tempos (`final_pass_done`,
  `final_pass_applied`, `final_pass_kept_draft`, `final_pass_skipped reason=…`). Medição em
  `docs/medicao-duas-passadas.md` (US$ 0,20 gastos); os modelos e o custo mudaram com a medição dos modelos
  da nuvem (acima).
- **Concordância só pela terminação** na guarda (`ProofreadingGuard.wordEdit`): radical comum de 4+
  letras e fim de até 3 letras (ou "-ou/-aram", "-eu/-eram"), ou um par da lista fechada de verbos
  (é/são, foi/foram, está/estão…). Número, negação, hiper/hipo, "prednisona"→"prednisolona",
  "amoxicilina"→"ampicilina", "direito"→"esquerdo", "normal"→"anormal" continuam recusados. A mistura
  (P157) volta ao ditado as trocas de concordância da frase em que alguma palavra foi recusada.

- **Pontuação falada**, portada do Intelligent Keyboard (`SpokenPunctuation`, com os casos de teste
  de lá): "vírgula", "ponto final", "dois pontos" (também "2 pontos"), "ponto e vírgula",
  "interrogação", "exclamação", "reticências", aspas, parênteses, "nova linha", "novo parágrafo"
  e, para listas, "novo item"/"próximo item" (linha nova com "- "). Aplicada a cada trecho fechado,
  na nuvem e no motor do aparelho, antes do vocabulário do usuário; um "vírgula" dito sozinho tira
  o ponto que o modelo pôs no trecho anterior pela regra da P144. Na captura do usuário no S26
  (2026-09-28) "ponto final Nova Linha" saía escrito. Não conferido no aparelho com fala.

### Corrigido

- **"Nova linha" dito sozinho depois de uma pausa sumia.** O pedaço virava só "\n", e a emenda
  (`TranscriptOverlap`, `LivePreviewAssembler`) tirava as pontas com `trim()` e o descartava como
  vazio: no S26 (2026-09-28) o pedaço tinha 10 caracteres e nada chegou ao campo. Agora só espaço sai
  das pontas, no campo e na nota.
- **Comando partido pelo corte no teto saía por extenso** ("nova linha: amanhã…", S26 2026-09-28).
  No motor do aparelho, o corte sem pausa segura para o pedaço seguinte as palavras que começam um
  comando sem terminá-lo ("nova", "ponto de", "dois"). Se não era comando, a palavra só entra um pedaço
  depois.

### Alterado

- **Modelos padrão** (medição de modelos da nuvem, regra: nenhum erro que muda sentido — afebril/febril,
  número, negação, remédio trocado —, depois nota de ortografia e pontuação, latência e custo): passada final
  num passo só com `thinkingmachines/inkling` (0 erros de sentido, nota 97,7, 4,2 s num ditado de 20 s), no
  lugar de `openai/gpt-transcribe` + `anthropic/claude-haiku-4.5`; transcrição ao vivo
  `openai/gpt-4o-mini-transcribe` (0 erros, nota 95,8, 1,6 s) no lugar do `openai/gpt-transcribe` (1 erro:
  "dipirona" → "de pirona"); formatação por LLM, quando escolhida, `openai/gpt-4.1`. Quem escolheu um modelo em
  Ajustes continua com ele. Passada final ≈ US$ 0,0049 por minuto (antes US$ 0,0073). Estimativa da tela da
  chave: ≈ R$ 0,70 a 2,90 por hora na nuvem (o teto com a revisão final) e ≈ R$ 1,60 a 1,90 no motor do
  aparelho com a revisão.
- **Textos:** Ajustes ("Modelo de transcrição", "Formatação", motor Nuvem, revisão final), README, política
  de privacidade e "O que o FlowVoice vê": na nuvem com a revisão ligada o áudio do ditado inteiro vai de
  novo no fim, e o texto só vai a um modelo de formatação quando há um.

- **Formatação:** prompt novo (pontuação, vírgulas, maiúsculas, acentos, concordância só pela terminação,
  listas só onde houve comando), temperatura 0 e modelo padrão `anthropic/claude-haiku-4.5` no lugar do
  `openai/gpt-4o-mini`, também na revisão final da nuvem. Estimativa de custo da tela da chave: ≈ R$ 1,90
  a 3,30 por hora na nuvem, e uma linha nova para o motor do aparelho com a revisão (≈ R$ 2,40 a 2,80).
- **Textos:** Ajustes, "O que o FlowVoice vê", tela da chave, motor de transcrição e política de
  privacidade dizem que, com a revisão final ligada, o áudio vai à OpenRouter no fim mesmo no motor do
  aparelho.

- **Motor do aparelho: o texto entra no campo a cada ~1,2 s**, e não mais só nas pausas ou a
  cada ~4 s. Cada corte ali só fecha o texto do fluxo contínuo e não custa requisição, então a
  sessão local usa teto de 1,2 s e pausa a partir de 0,8 s de áudio
  (`DictationPipeline.LOCAL_WINDOW_TARGET_MS`, `LOCAL_MIN_BUFFERED_MS`); a nuvem segue com 4 s e
  2 s. Continua fechando só palavras inteiras e segurando começo de comando falado. Não medido
  com fala no S26.

### Conferido no S26 (motor Nuvem com modelos, release, 2026-09-28 tarde)

Frases de `audio-pessoal` tocadas pelo alto-falante do Mac, bolha → Samsung Notes (notas novas), motor Nuvem,
revisão final ligada (capturas e logs em `/tmp/flowvoice-nuvem/`):

- Ajustes lista os modelos medidos primeiro com "N erros de sentido · nota · tempo"; padrões
  `gpt-4o-mini-transcribe` ao vivo e "Um passo só: thinkingmachines/inkling" (capturas 01, 02, 05).
- 01+11+12+13 (26,3 s), padrões: o passo único devolveu erro do provedor (`final_pass_kept_draft
  kind=server reason=erro_transcricao`, 8 s) e ficou o texto ao vivo, com aviso. No Mac, na mesma hora, o
  inkling passou a raciocinar por padrão (11–20 s numa frase), a devolver vazio e 429; o app agora o pede com
  raciocínio desligado.
- Contínua de 20 s (26,8 s), padrões, depois disso: `final_pass_done mode=um_passo transcribeMs=7362` →
  "Paciente do leito 12 segue com dispneia, pedida uma radiografia de tórax. Ela está eupneica, sem sinais de
  desconforto respiratório. Hemograma veio normal, só a glicemia um pouco alterada. A tomografia mostrou uma
  broncopneumonia à direita, solicitada transferência para a UTI para o paciente que estava na enfermaria."
  — contra o gabarito corrigido: "pedida" (pedi), "solicitada" (Solicitei), sem o "O" de "O hemograma"; nenhum
  erro que muda sentido; 7,4 s do toque de parar ao texto final (4,2 s no Mac pela manhã).
- Trocada a formatação para "Sem formatação" em Ajustes, 01+11+12+13 de novo: `mode=so_transcricao
  transcribeMs=1923` → "Bom dia, tudo bem? Consegue me ligar mais tarde? Paciente do leito 12 segue com
  dispneia. Pedi uma radiografia de tórax. Aumentei a dipirona para 6 em 6 horas. Ela está eupneica, sem
  sinais de desconforto respiratório." — igual ao gabarito corrigido, salvo algarismos e um ponto no lugar de
  vírgula.
- Ajustes devolvidos como estavam: motor Nuvem, `x-ai/grok-stt-1.0`, revisão final ligada, formatação no
  padrão (passo único, inkling).
- **Não conferido:** fala ao vivo, rede móvel, motor do aparelho com os novos modelos, barra de revisão e notas.

### Conferido no S26 (duas passadas, release, 2026-09-28)

Frases de `audio-pessoal` e ditados longos tocados pelo alto-falante do Mac, bolha → Samsung Notes, motor
do aparelho, revisão final ligada (capturas em `/tmp/flowvoice-duas-passadas/`):

- 01+11+12+13 com pausas (26,4 s): rascunho "Bom dia tudo bem consegue me ligar mais tarde paciente do
  leito doze segue com é pedi uma radiografia de toques aumentei de pirana…" → final "Bom dia, tudo bem?
  Consegue me ligar mais tarde? Paciente do leito 12 segue com dispneia, pedi uma radiografia de tórax.
  Aumentei a dipirona para 6 em 6 horas. Ela está eupneica, sem sinais de desconforto respiratório." em
  4,3 s do toque de parar (transcrição 2,4 s + formatação 1,7 s).
- Contínua de 20 s (26,7 s): igual ao gabarito salvo o que o gabarito difere da fala; 3,5 s. Os
  espaços comidos e a quebra de parágrafo do Samsung Notes no rascunho ("elaestá") somem na troca.
- Conversa com pausas (65 s) e texto 3 (55,5 s): **dois pedaços** cortados em pausa (45,7 + 19,3 s;
  44,2 + 11,3 s), transcritos em paralelo; 4,4 s e 4,6 s até o texto final.
- Modo avião antes de parar: 4 tentativas com `kind=network`, `final_pass_skipped reason=erro_transcricao`
  ~3 s depois, rascunho intacto e cartão "Versão final da nuvem não veio: ficou o texto do aparelho".
- **Não conferido:** fala ao vivo, rede móvel lenta, notas do FlowVoice e barra de revisão no aparelho
  (só JVM), outros apps além do Samsung Notes.

### Removido

- **Cartão da prévia no ditado normal.** Ele cobria a linha em que se ditava e repetia o que
  já estava no campo (capturas do usuário no S26, 2026-09-28). Agora só abre para pendente depois
  de troca de app, aviso, "revisando…" ou bolha oculta; sem "Cancelar" no ditado normal.
- **Cartão "Digitado no campo · N palavras" depois do ditado.** O texto já está no campo; o
  cartão só aparece quando o ditado termina com aviso (trecho que falhou, captura interrompida,
  teto), que é o único lugar onde o aviso é mostrado.

## [0.7.0] - 2026-09-28

Motor de transcrição no aparelho: NVIDIA Nemotron 3.5 ASR Streaming 0.6B (blocos de 560 ms,
int8) pelo sherpa-onnx 1.13.8, com o texto aparecendo enquanto se fala. É uma alternativa à
nuvem (OpenRouter), escolhida em Ajustes; o padrão continua sendo a nuvem. Conferido no S26
Ultra (Android 16) em 2026-09-28 com frases gravadas tocadas pelo alto-falante do Mac ao lado
do telefone, não com fala ao vivo.

### Adicionado

- **"Motor de transcrição" em Ajustes:** "No aparelho (Nemotron, ao vivo)" ou "Nuvem
  (OpenRouter)". O motor do ditado sai de uma regra pura (`TranscriptionEngineSelection`): o
  local só vale escolhido, com o modelo inteiro no aparelho e no Android; qualquer outro caso é
  a nuvem, como antes. O motor fica fixo durante o ditado.
- **Modelo baixado no app:** 475 MB do GitHub (release `asr-models` do k2-fsa/sherpa-onnx), com
  progresso, conferência de tamanho e SHA-256, espaço livre conferido antes (1,2 GB durante a
  instalação), extração em streaming do `.tar.bz2` (só os quatro arquivos, guarda contra path
  traversal, tamanhos exatos, troca atômica da pasta), cancelar e apagar. Fica em `filesDir`
  (682 MB), fora do backup e da transferência entre aparelhos. No S26: download em 73 s, SHA-256
  ok, instalado em 116 s no total.
- **Texto ao vivo:** o que o motor já reconheceu e ainda não fechou aparece como provisório
  (sublinhado pontilhado) no cartão da bolha, na barra de revisão e na nota. **Nunca é digitado
  em outro app:** só o pedaço fechado segue o caminho de sempre — digitação direta com as travas
  de destino e a conferência do campo, PENDENTE na troca de foco, barra com Inserir e nota.
- **Chave opcional com o motor local:** o ditado começa sem chave. O passo 3 do onboarding virou
  "Chave OpenRouter ou modelo no aparelho", e qualquer um dos dois basta; a tela da chave oferece
  "Baixar o modelo (475 MB)". A revisão por IA continua precisando da chave e é pulada sem ela
  (`dictation_proofread_skipped reason=sem_chave`). A estimativa de custo fica só na parte da
  nuvem.

### Como o texto é fechado (decisões)

- **Um fluxo só por ditado.** No teclado, um fluxo novo por trecho perdia palavras quando o
  corte caía dentro delas. Os cortes do FlowVoice continuam os mesmos (pausa, teto de ~4 s, fim);
  cada corte fecha o texto reconhecido desde o anterior.
- **Corte numa pausa ou no fim:** o motor recebe 0,8 s de silêncio de cauda e tudo o que foi dito
  sai. **Corte no teto, com a fala em curso:** fecham só as palavras inteiras — até antes do último
  token que abre palavra —, e a última palavra, que ainda pode crescer, fica para o pedaço
  seguinte. Nenhum corte parte palavra.
- **Pedaço que continua a palavra anterior entra colado.** Com 0,6 s de cauda, no S26, o "s" de
  "minutos" só saiu depois do fechamento e virou "minuto s"; a cauda passou a 0,8 s e o pedaço
  cujo primeiro token não abre palavra (ou que começa com pontuação, como o "?" de "tarde?")
  entra sem espaço na digitação, na revisão, na nota e na prévia.
- **Sem deduplicação na emenda:** os pedaços do fluxo contínuo não se sobrepõem, então a emenda
  por sobreposição da nuvem não se aplica — ela apagaria a palavra que o usuário repetiu.
- **Sem teto de requisições nem travas de áudio vazio** (nada disso custa aqui); no lugar, o
  ditado local para em **10 min** (`LOCAL_SESSION_CAP`), com o texto até ali e o aviso "Ditado
  encerrado no limite de 10 min".
- **Falha do motor no meio do ditado:** o que já foi reconhecido — inclusive o provisório que
  estava na tela — é entregue, o ditado termina com o aviso "Motor no aparelho falhou aos M:SS:
  texto só até ali", e o FlowVoice **não** troca para a nuvem sozinho, mesmo com chave.
- **Idioma `pt-BR`** por fluxo (no modelo, `pt` é pt-PT), só `greedy_search` (sem hotwords).
- **Reconhecedor por processo:** carrega uma vez (1,4–1,7 s no S26), é contado por uso, sai da
  memória depois de 5 min sem ditado ou nos pedidos urgentes de `onTrimMemory`. O áudio que chega
  durante a carga espera na fila e é transcrito.

### Conferido no S26 (release 0.7.0, 2026-09-28)

- Instalação do modelo pela tela da chave, no onboarding, sem chave; o passo 3 ficou ok.
- Ditado pelo microfone do Início e pela bolha no Samsung Notes, e em notas do FlowVoice, com as
  frases 01–20 de `intelligent-keyboard/build/voz/audio-pessoal` e uma frase contínua de 20 s
  (cinco frases emendadas, sem pausa) para forçar cortes no teto. Carga do modelo 1421–1670 ms
  (0 ms com o reconhecedor já carregado); fechamento com cauda 48–142 ms; tempo do motor por trecho
  de 11 a 22 % do áudio do trecho, fechamento incluído; intervalo médio entre mudanças do
  provisório de 0,84 a 1,19 s por sessão, pausas incluídas. Nenhuma queda. Em modo avião, uma nota
  foi ditada inteira.
- Qualidade do Nemotron nas frases de trabalho ruim como no teclado ("dispneia" → "Spneia",
  "dipirona" → "de pirona"); o texto de cada teste, comparado às referências, está em
  `TAREFAS_PENDENTES.md` (seção do motor no aparelho).
- **Não conferido:** fala ao vivo (só alto-falante), consumo de bateria, memória além de uma
  leitura (PSS 1,47 GB e RSS 467 MB com o modelo carregado e ocioso), outros apps além do
  Samsung Notes.

### Corrigido

- **Emenda da nuvem não quebra mais com janela que só repete uma palavra (LOC-overlap).**
  `TranscriptOverlap.match("… muito muito obrigado", "muito")` indexava além da janela
  (`IndexOutOfBoundsException`). Quando a janela inteira já estava no texto antes do último pedaço,
  a emenda agora não acrescenta nada.

### Build

- sherpa-onnx 1.13.8 pelo JitPack (`com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8`), o mesmo
  arquivo do release oficial do GitHub (SHA-256 `633c2432…bd96`, conferido byte a byte), preso em
  `gradle/verification-metadata.xml` só para esse grupo; commons-compress 1.28.0.
- APK só com `arm64-v8a` (S26 e os emuladores arm64 deste Mac); as APIs C/C++ do AAR ficam de
  fora. APK de release: 11,46 MB (0.6.0) → 39,64 MB (+28,2 MB, quase tudo `libonnxruntime.so`).
- Regra de keep do R8 para `com.k2fsa.sherpa.onnx.**` como consumer rule do `:shared` (o release
  segue sem minify).
- versionCode 17.

## [0.6.0] - 2026-09-27

Primeiro uso sem abandono e preparação para o teste interno do Google Play. Um
smoke num emulador Android 16 (AVD `flowvoice_api36`, 2026-09-27) passou pelo
fluxo novo e achou os dois defeitos de P163 e P164, corrigidos e conferidos no mesmo
emulador. Nada desta versão foi conferido num aparelho real.

### Aviso de malware (P161): causa medida no aparelho

- **O app acusado não era o FlowVoice.** No S26 (2026-09-27), a "Proteção do aplicativo" da
  Samsung (McAfee) listava como malware o "Intelligent Keyboard" `…latin.benchmark`, um build
  de depuração de outro projeto assinado com a chave pública do AOSP; o Play Protect não
  acusava nada. A hipótese da 0.5.1 (FlowVoice debug com acessibilidade) não se confirmou. O
  release do FlowVoice passou pela verificação do Play Protect na instalação
  (`VERIFICATION_ALLOW`), e a nova verificação da Samsung, com o teclado trocado, deu "Não há
  nenhuma ameaça".

### Distribuição

- **`compileSdk` e `targetSdk` 36**, exigência do Play para apps novos desde
  2026-08-31.
- **CI gera o `.aab` assinado** junto do APK de release; `:androidApp:bundleRelease`
  faz o mesmo localmente.
- **`docs/DISTRIBUICAO_PLAY.md`:** checklist do teste interno (conta, Play App
  Signing, formulários, testadores).
- **`docs/POLITICA_PRIVACIDADE.md`:** rascunho da política de privacidade, ainda com
  campos `[PREENCHER]` e sem URL publicada.

### Onboarding (P162)

- **"O que o FlowVoice vê":** tocar no passo 1 abre, antes do Android, uma tela que
  diz o que a acessibilidade acessa e para quê: o nome do app na tela; no ditado,
  o campo com o cursor; a posição de campo, cursor e teclado. Ela diz também o que
  o serviço não faz: o texto lido fica no celular, ele não escreve em campo marcado
  como senha e não navega por você. Avisa que o Android vai falar em "controle
  total", tem o link da política de privacidade e só abre a Acessibilidade com o
  toque em "Entendi, abrir Acessibilidade". É a divulgação em destaque que o Google
  Play exige de quem usa AccessibilityService sem ser ferramenta de acessibilidade
  ([política](https://support.google.com/googleplay/android-developer/answer/10964491)).
  O botão "Abrir configurações de acessibilidade" do Diagnóstico passa pela mesma
  tela.
- **Configurações restritas em passos:** para quem instalou fora de loja no Android
  13+, o aviso virou três passos numerados que avisam antes o "acesso negado" do
  Android. "Abrir detalhes do app" virou botão.
- **Tela própria da chave (passo 3):** explica a OpenRouter, abre openrouter.ai/keys
  e informa os créditos pré-pagos no cartão (mínimo US$ 5, ≈ R$ 32 com taxa e IOF).
  Mostra uma **estimativa** de ≈ R$ 1,90 a 2,70 por hora de ditado (set/2026), feita
  com os preços da OpenRouter de 2026-09-27, a taxa de 5,5% (mínimo US$ 0,80), o IOF
  de 3,5% e a PTAX de 2026-09-25 (5,1991), e não medida em uso. As premissas ficam
  em `DictationCostEstimate`. "Validar e salvar" é o mesmo código de Ajustes
  (`OpenRouterKeyEntry`). "Configurar chave" do Início e das Notas abre essa tela.
- **Progresso persistido:** o onboarding conclui sozinho quando os três passos ficam
  ok, e "Pular por enquanto" fica guardado. O botão escuro leva ao próximo passo
  pendente e pular virou ação secundária.
- **Textos honestos:** "Três passos…"; o microfone "grava só durante o ditado"; a
  chave "só vai à OpenRouter, nunca é sincronizada"; o login sem Client ID diz
  "Começar" e não promete sincronização; o rodapé diz "notas e dicionário ficam
  neste celular"; o Início diz "Toque para ditar".

### Adicionado

- **Link para a política de privacidade nos Ajustes:** nova entrada "Política de privacidade — O que o FlowVoice envia, guarda e lê", logo abaixo de "Diagnóstico técnico". Abre `https://github.com/devrafaelbrauner/FlowVoice/blob/main/docs/POLITICA_PRIVACIDADE.md` no navegador. A URL fica numa constante única (`AppLinks.PRIVACY_POLICY_URL`), que o onboarding também pode usar. O link só funciona depois que `docs/POLITICA_PRIVACIDADE.md` chegar à `main`, e a política ainda tem campos `[PREENCHER]`.


### Corrigido

- **Botão flutuante liga de primeira (R5b):** ao ligar "Botão flutuante" sem a permissão "sobrepor a outros apps", o FlowVoice guarda o pedido. Na volta da tela do sistema, se a permissão foi dada, o fluxo continua sozinho: pede notificações (se faltar) e liga a bolha. Se não foi dada, mantém o aviso "Autorize “sobrepor a outros apps” e ligue de novo.". O microfone do Início faz o mesmo: o ditado começa na volta, sem outro toque.
  - O app já abria `ACTION_MANAGE_OVERLAY_PERMISSION` com `package:`. A lista genérica de apps não é defeito do emulador nem do fabricante: a partir do **Android 11** o sistema ignora o pacote e sempre mostra a lista de apps ([documentação](https://developer.android.com/about/versions/11/privacy/permissions)). Do Android 6 ao 10 a página abre direto no FlowVoice. Por isso a mensagem diz "Ative “sobrepor a outros apps” para o FlowVoice".
- **Sem chave, aviso que fica e leva à configuração (R5c):** o microfone do Início, "Nova nota" e "Ditar nesta nota" conferem a chave OpenRouter antes de começar. Sem chave, nada de ditado nem de nota "Sem título" vazia. Aparece o aviso fixo "Falta a chave OpenRouter", com **Configurar chave** (abre a tela própria da chave pelo `onOpenKeySetup` do `FlowVoiceRoot`) e **Agora não**. O aviso some quando a chave é salva ou quando o usuário dispensa. Pela bolha, o cartão de falha continua como antes.
- **A bolha não cobre mais o próprio app (R5d):** com qualquer tela do FlowVoice aberta, a bolha ociosa fica escondida; o serviço segue ativo e o interruptor continua ligado. Ela volta quando o app sai da frente.
  - **Decisão:** só a bolha ociosa some. Isso vale para o modo "só bolha", inclusive durante ditados de nota ou ditados que não são da bolha.
  - Com a prévia de um ditado direto na tela, a bolha fica, porque ela é o "parar" desse ditado (P159).
  - Com a barra de ditado, nada muda: a barra já substitui a bolha.
  - Assim, nenhum ditado da bolha é cancelado nem perde os controles. "Ocultar botão" e as regras P40/P159/P160 continuam iguais.
  - Ao ligar o botão nos Ajustes, a mensagem agora avisa que ele aparece quando você sai do FlowVoice.
- **P163: a validação da chave aceitava qualquer chave.** Ela consultava o
  `/api/v1/models`, que responde 200 com chave falsa e até sem chave (curl,
  2026-09-27). No smoke, `sk-or-v1-FAKE-0000000000000000` foi salva e o passo 3 ficou
  "ok". Agora a validação vai ao `/api/v1/key`, que devolve 401 para chave inválida ou
  ausente ([documentação](https://openrouter.ai/docs/api/api-reference/api-keys/get-current-key)).
  A classificação não mudou: 401/403 recusa, e rede ou 5xx dá "não foi possível
  validar agora", sem apagar a chave salva. **Conferido no emulador:** a mesma chave
  falsa agora dá "A OpenRouter recusou esta chave." e não é salva. O caminho de chave
  válida (200) segue a documentação e os testes, mas não foi exercido, por falta de uma
  chave real. A lista de modelos continua no `/api/v1/models`, que é público; o
  `INVALID_KEY` dela só aparece se a OpenRouter passar a autenticar a lista.
- **P163: a mensagem de formato diz a regra que falhou.** `sk-or-v1-FAKE-000` recebia
  "Formato inválido: a chave começa com sk- e não tem espaços.", sendo que ela começa
  com sk-. Agora a mensagem é "Chave curta demais: tem 17 caracteres, e uma chave da
  OpenRouter tem pelo menos 20. Copie a chave inteira." (e há mensagens próprias para
  prefixo e espaços). O Windows usa as mesmas mensagens.
- **P164: a bolha escondida reaparecia sobre o FlowVoice.** Depois de sair para o
  launcher e voltar ao app, a bolha continuava desenhada sobre o Início e o
  Diagnóstico, mesmo 10 s depois. O WindowManager dava a janela como 0×0
  (`frame=[850,749][850,749]`), mas o SurfaceFlinger seguia compondo o último quadro
  (`displayFrame=[850 749 1049 948]`). Esvaziar o conteúdo não bastava: agora a
  janela do overlay fica `GONE` e com `FLAG_NOT_TOUCHABLE` sempre que não teria nada a
  mostrar. **Conferido no emulador:** a sequência do smoke (repetida duas vezes e
  também com o app em paisagem) deixa a janela `mViewVisibility=0x8`, fora das camadas
  compostas e da lista de janelas de toque. A bolha volta no launcher, e numa prévia de
  ditado direto (Mensagens) a bolha continua visível e encerra o ditado ao toque.

### Corrigido na revisão de código (REV)

Revisão do projeto inteiro em duas partes: `shared/` e `desktopApp/` (REV1), depois
`androidApp/` e o CI (REV2). Cada achado corrigido tem um teste de regressão que falhava
antes da correção. Nada desta seção foi conferido num aparelho real; o que passou pelo
emulador está marcado.

**Texto que se perdia ou mudava de sentido**

- **REV1-R1: uma janela alta que volta vazia não cala mais o resto do ditado.** Uma
  tosse ou um esbarrão devolvidos sem texto ensinavam à memória da P153 que aquele
  volume era silêncio, e toda fala mais baixa deixava de ser enviada, sem aviso. Agora
  a memória só aprende com janelas de até 2× o limiar de fala da sessão, e a janela alta
  sem texto vira trecho falho ("voz sem texto"), com o aviso de texto incompleto.
- **REV1-R2: a revisão por IA não aceita mais trocas que mudam o sentido clínico:**
  hipertensão→hipotensão (hiper/hipo), prefixo de negação posto ou tirado
  (normal→anormal, regular→irregular, sintomático→assintomático), remédio trocado por
  outro parecido (prednisona→prednisolona, amoxicilina→ampicilina), número por extenso
  trocado (sessenta→setenta), "?" novo e pontuação nova logo depois de não, nem, nega
  ou sem. A regra de contraste no começo da palavra (P156) virou um utilitário comum
  (`OnsetContrast`). A P149 (tá→está, pontuação) continua valendo.
- **REV1-R3: microfone que cai no meio do ditado não leva mais o texto junto.** A falha
  de captura com o ditado em curso é tratada como parada antecipada, igual à chave
  recusada (P111): o áudio guardado vira a última janela, as janelas na rede são
  esperadas, e o texto chega à revisão, à nota ou ao campo com o aviso "Captura do
  microfone interrompida aos m:ss: texto só até ali". Sem texto nenhum, falha como antes.
- **REV1-Y1:** uma resposta 200 com erro do provedor, ou sem `text`, é falha da janela
  (repetida se o código for 5xx), não um trecho vazio.
- **REV1-Y2: depois de "Inserir aqui" noutro campo, a revisão final não reescreve mais
  o campo.** Num campo que não se deixa ler, ela apagava o texto do usuário e escrevia
  ali o trecho do primeiro campo.
- **REV1-Y3:** o fim da última palavra não se perde no toque de parar. O quadro que o
  parar destrava entra na última janela, e o resto do buffer do microfone é lido antes
  de fechá-lo (não medido no S26).
- **REV1-Y4:** a conferência do pedaço digitado se ancora no que o próprio app escreveu
  e não apaga nem duplica o pedaço anterior quando o editor aplica um pedaço atrasado.
- **REV2-Y1 e parte da P86: o FlowVoice só lê o texto antes do cursor depois de
  conferir** que o foco continua no app e no campo do ditado, que o campo não é o do
  próprio FlowVoice (chave OpenRouter) e que não é campo de senha. O fallback por
  acessibilidade também recusa senha pelo tipo do input (`VISIBLE_PASSWORD`,
  `WEB_PASSWORD`) antes de ler o texto. A divulgação diz agora "não lê nem escreve em
  campos marcados como senha".
- **REV2-Y5:** a reescrita atômica do campo (P142) só roda num EditText simples cujo
  texto aparece por inteiro para o FlowVoice. Em editores ricos, web ou expostos em
  parte, o campo fica como está e a divergência vai só para o log.
- **REV1-N2:** o vocabulário do usuário é reaplicado depois da revisão final do ditado
  direto, como já era na revisão antes de inserir.

**Dados guardados**

- **REV1-Y7:** sincronizar não carimba mais todas as notas com a hora do sync; a edição
  mais nova de outro aparelho não se perde. A nota apagada vira lápide sem texto
  (`deletedAtMs`) e não volta pelo sync. Notas gravadas antes continuam sendo lidas.
- **REV1-Y8: uma nota ilegível no armazenamento não apaga mais as outras.** Cada nota é
  lida sozinha, o texto original vai para um backup (`notes_json.bak`) e, se nada é
  legível, o app não grava por cima. Preferências ilegíveis também vão para backup antes
  de voltar ao padrão.
- **REV1-N3:** o dicionário guarda a ordem de aprovação (lista JSON). Os termos já
  aprovados são migrados em ordem alfabética, porque o formato antigo não tinha ordem.
  **Conferido no emulador Android 16:** atualizando por `adb install -r` sobre a 0.6.0
  anterior (`5a7fc54`, mesma chave), os três termos aprovados continuaram na lista, e
  continuaram depois de um force-stop. As notas não puderam ser exercidas: sem chave, não
  há como criar nota em nenhuma das duas versões.
- **REV1-N4:** notas novas têm id UUID. **REV1-N9:** no máximo 100 sugestões pendentes
  no dicionário.
- **REV2-Y4:** um termo aprovado pode ser removido na tela do Dicionário e deixa de ser
  aplicado. **Conferido no emulador:** "Remover dipirona do dicionário" tirou o termo, e
  a remoção continuou depois de um force-stop.
- **REV2-Y6:** editar uma nota não regrava mais todas as notas a cada tecla na thread
  principal. A gravação sai 500 ms depois da última tecla, em segundo plano, e na hora
  quando a tela pausa.

**Bolha, Início e rede**

- **REV2-R1: com "Ocultar botão" no ditado direto, o cartão da prévia voltou a
  responder.** A correção da P164 punha `FLAG_NOT_TOUCHABLE` em todas as janelas do
  overlay, inclusive no cartão com Encerrar, Cancelar e Inserir aqui. Agora só a janela
  da bolha escondida deixa de receber toques (`OverlayWindowFlags`). **Conferido no
  emulador** só o lado da bolha: com o app aberto, depois de voltar do launcher e depois
  de girar, a janela da bolha fica `mViewVisibility=0x8` com `NOT_TOUCHABLE`, e no
  launcher volta visível. O cartão tocável precisa de um ditado real, sem chave no
  emulador.
- **REV2-Y2:** o microfone do Início não religa mais para sempre a bolha que o usuário
  desligou. O overlay aparece só para aquele ditado, e o interruptor dos Ajustes fica
  como estava.
- **REV2-Y3:** o app de volta do microfone do Início vence 10 min depois que o usuário
  sai dele. Sem destino recente, o FlowVoice só vai para trás e o primeiro trecho espera
  "Inserir aqui". Os dois caminhos da P130 (Início → ícone e Recentes) continuam.
- **REV1-Y5: o fim do ditado tem prazo de 15 s.** Com a rede presa, o "Transcrevendo"
  durava minutos. Vencido o prazo, o que voltou é entregue e os trechos sem resposta
  entram no aviso. **REV1-N8:** o teto de 5 s da revisão por IA (P152) vale também antes
  de inserir e nas notas.
- **REV1-Y9:** um 429 ou 5xx do `/api/v1/models` é falha, não lista vazia, e nem falha
  nem lista vazia ficam 24 h em cache.
- **REV1-Y6: o desktop avisa do trecho que falhou** ("Trecho X de Y falhou") e encerra a
  gravação no teto de requisições ou com a chave recusada, como o Android. A tela do
  desktop não foi aberta.
- **REV1-N1, N6, N7, N11 e P53:** o texto ao vivo tira a repetição do contexto como o
  final; um 400/422 no pedido com tempos só marca o modelo como "sem tempos" se o mesmo
  áudio passar em `json`; cancelar o ditado não derruba o benchmark; a emenda exata entre
  janelas não apaga mais do que 1 s de contexto comporta; o controle da transcrição
  ficou seguro entre threads.
- **REV2-Y7: CI** com token só de leitura (`permissions: contents: read`), actions
  fixadas por SHA e keystore apagado mesmo quando o build de release falha.
- **REV2, pequenos:** cursor que não se reposiciona no meio do texto desfaz a escrita e
  deixa o trecho pendente; a notificação reabre a tarefa existente; o seletor de
  compartilhamento não vira destino do Início; o Diagnóstico exporta o modelo escolhido;
  a descrição do serviço nos Ajustes do Android diz o que é lido; os Ajustes não
  sobrescrevem preferências com cópia velha; o release não consulta o marcador de log;
  `storeFile` relativo resolve a partir da raiz do repositório, como no README.
- **REV1-N5 e N10:** o comentário do teto de requisições diz que ele conta janelas, não
  chamadas (pior caso com as tentativas ≈ US$ 0,14 por sessão); o pacote desktop passou
  a 0.6.0.

### Removido

- **Estatísticas do Início (R5e):** os cartões "Latência média", "Ditados hoje" e "Gasto hoje" sempre mostravam "—" e saíram, junto com o componente `StatCard`, até existir fonte persistida (P59).

## [0.5.1] - 2026-09-27

Release assinado e ícone do app, contra o aviso de malware no S26 (P161).

### Security

- P161: **o app instalado passa a ser o release assinado com chave própria.** O
  APK debug que o README e o CI produziam é depurável e assinado pela chave
  genérica `CN=Android Debug` (no CI, certificado novo a cada build). Somado a
  acessibilidade, sobreposição e microfone, é a causa provável do aviso de
  malware no S26 — hipótese: o aviso não foi observado no aparelho, nem qual
  proteção o deu. O release lê a chave de `keystore.properties` (fora do git) ou
  das variáveis `FLOWVOICE_*`, não é depurável e não leva o receiver de teste do
  adb. Sem chave, sai sem assinatura como antes.
- P161 / SEG-2: o CI não publica mais o APK debug; em `main`, com os segredos da
  chave, publica `flowvoice-release` (APK assinado + `.sha256`) e confere a
  assinatura com `apksigner`.

### Added

- P19: **ícone do app**, provisório: microfone laranja sobre fundo escuro, com as
  cores do tema. Ícone adaptativo no Android 8+ e o mesmo desenho num círculo no
  Android 7.

### Fixed

- P149: **dois-pontos ou aspas na revisão não descartam mais a pontuação.** No S26
  (2026-09-17 12:46) a revisão pôs "manhã:" e o guard derrubou o texto inteiro
  (`dictation_proofread_skipped reason=guard`), deixando o ponto cedo no campo.
  O vão com sinal novo volta à pontuação do ditado; a vírgula da revisão entra.

- P156: **a emenda não apaga mais contraste no prefixo.** "exportações" e
  "importações", "hipertensão" e "hipotensão", "redução" e "indução" ficavam
  a 1–2 edições e o degrau 4 as tomava pela mesma palavra (ou pela palavra
  frouxa). Agora diferença nas primeiras 4 letras de uma palavra de 8+ é
  outra palavra, e a palavra frouxa só vale para encurtamento da fala
  ("tá"/"está", "tava"/"estava"). "leucocitose"/"leucositose" (miolo) e o
  caso medido da P150 ("está"/"Tá muito bonito") continuam emendando.
  Falta o canário hiper/hipo com fala no S26.

- P145: **o vocabulário do usuário conserta termo raro que o modelo escreveu
  errado.** No S26, "dispneia" saiu "de Espinéia" e "na praia" saiu "napraj" — uma
  palavra virando duas e duas virando uma, que a troca de termo exato não
  alcançava. Agora o termo é procurado pelo som, em janelas de uma a quatro
  palavras, e a troca exige **o mesmo esqueleto de consoantes**, diferença
  pequena e proporcional ao tamanho, e sinal de que houve erro (a fronteira mudou
  ou o termo é longo). Por isso "hipertensão" nunca vira "hipotensão", "na praça"
  nunca vira "na praia" e flexão não é corrigida. A correção entra antes de o
  trecho ir ao campo, então a contagem do que foi escrito (P148) continua exata.
  Log `dictation_vocabulary_applied`, sem o texto ditado.
- P142: **o campo é conferido depois de cada trecho, e o que não entrou é
  refeito.** O espaço da emenda sumia e o ponto que a P144 manda apagar às vezes
  ficava — em dois ditados o campo tinha um caractere a mais do que o app
  contava. Agora, antes de apagar, o app confirma no campo que o caractere está
  mesmo lá; depois de escrever, relê e, se o fim do campo não for o que foi
  pedido, refaz aquele pedaço pela rota atômica (`ACTION_SET_TEXT`), que troca o
  texto num passo só. No Android, a composição do teclado é encerrada com um
  `commitText` vazio antes do apagar, já que a conexão de acessibilidade não
  expõe `finishComposingText`. Qualquer falha deixa o campo intacto, com
  `dictation_write_mismatch acao=` dizendo o que houve.
- P142: **o espaço que sumia volta no fim do ditado.** Medido no S26: o app
  escreve " mostrou…" com o espaço e o campo fica "sanguemostrou" — o editor come
  o espaço inicial quando ele vem logo depois de um apagar. A conferência feita na
  hora não enxerga isso, porque a leitura chega antes de o editor aplicar a
  escrita (foi medido um campo com o apagar já feito e o texto ainda não). Agora,
  campo sem nada do que acabou de ser escrito é tratado como leitura velha e nada
  é reescrito — antes, essa leitura podia fazer o app escrever o trecho duas
  vezes. O conserto passou para o fim do ditado, onde o campo já está estável: se
  a revisão vier igual ao ditado mas o campo não for o que foi ditado, o texto
  ditado é reposto (`dictation_field_restored`), com a mesma comparação por letras
  da P148.
- P155: **a emenda reconhece a repetição quando a primeira palavra saiu
  diferente.** Num ditado de dois minutos, 3 de 30 emendas entraram dobradas —
  "do STF, No STF", "afirmações contundentes, informações contundentes",
  "julgada. Em julgado." — porque o segundo de contexto começa num ponto qualquer
  da fala e o modelo completa a primeira palavra cortada com outra. Agora essa
  primeira palavra pode divergir de três formas explicáveis (palavra curta
  trocada, final comum longo, palavra curta acrescida), desde que tudo depois dela
  case exato. Negação nunca é tolerada, e "a dose de dipirona" + "Nova dose de
  dipirona" continua inteiro. De quebra, dois defeitos antigos: "muito, muito
  cansado" perdia a repetição, e "já tomou remédio" + "Não tomou remédio" podia
  sumir inteiro. Limitação aceita: "o SAMU." + "No SAMU ninguém atendeu." perde o
  "No SAMU", porque tem a mesma forma do caso real.
- P155: **o começo cortado de uma palavra longa também é reconhecido quando o que
  vem depois prova a repetição.** "indicações dos ministros" dobrou duas vezes no
  S26, como "Ações dos ministros" e "Declarações dos ministros". Com duas palavras
  exatas depois, uma delas longa, um final comum de 5 letras passa a bastar, desde
  que o começo escrito pelo modelo não seja maior que o começo perdido. "Tratamento
  da pressão arterial" depois de "aumento da pressão arterial" continua inteiro.
- P154: **uma leitura vazia do microfone não mata mais o ditado.** O erro
  `audio capture read failed: code=0`, visto três vezes no S26, era **barulho de
  desligamento** — o `stop()` destravando a leitura pendente quando o usuário
  encerrava —, e não a causa de queda nenhuma: a ordem das linhas prova, porque o
  caminho de falha teria limpado o áudio acumulado e escrito `dictation_failed`,
  que não aparece em nenhum dos registros. Mas o caminho "uma leitura zero =
  sessão morta" existia de verdade, dependendo de quem vencesse a corrida. Agora
  leitura vazia é tolerada por 300 ms e, persistindo, o gravador é reaberto uma
  vez antes de desistir; objeto morto vai direto para a reabertura, e parâmetro
  inválido desiste na hora. Corrigida também uma corrida em que a thread de uma
  captura anterior podia sobreviver e entregar áudio por cima do ditado novo. O
  log passou a trazer o estado do gravador, as leituras vazias e as reaberturas —
  números, nunca áudio nem texto.

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
- P149: **uma palavra trocada não custa mais a pontuação do ditado inteiro.** No
  S26 (2026-09-16 10:42) a revisão trocou "Tá" por "Está" — palavra curta, que o
  guard não deixa mudar, pela mesma regra que barra "direito" por "esquerdo" — e
  o texto inteiro foi descartado, junto com a vírgula e as maiúsculas que estavam
  certas. Agora o ditado e a revisão são alinhados palavra a palavra: onde o
  guard aceita a palavra, entra a da revisão (acento, ortografia, maiúscula);
  onde não aceita, **fica a do ditado**, com a maiúscula que a revisão deu, para
  a frase não recomeçar com letra maiúscula depois de uma vírgula. Pontuação e
  espaços vêm sempre da revisão, e o resultado ainda passa pelo guard inteiro:
  aspas, dois-pontos, quebras de linha e marcas `<ditado>` continuam descartando
  a revisão. O texto misturado vai ao log da P135 (`proofreading_merged`).
- P152: a revisão final **desiste em 5 s**. Sem teto próprio valia o das
  transcrições (30 s), com o ditado já no campo e o usuário parado esperando o
  texto trocar; as duas revisões medidas no S26 levaram ~1,5 s. Estourando o
  teto, o ditado fica exatamente como foi digitado e o log registra
  `dictation_proofread_skipped reason=demorou`.
- P150: **a emenda reconhece o contexto transcrito com outras palavras.** O 1 s de
  áudio sobreposto da P143 pode voltar escrito diferente: no S26 (2026-09-16
  10:42) a janela anterior tinha "está muito bonito" e a nova veio "Tá muito
  bonito", a comparação por letras não reconheceu e "muito bonito" entrou duas
  vezes no campo. Agora, **só onde a comparação atual desiste**, o fim do texto já
  escrito é comparado com o começo do trecho novo deixando as palavras divergirem
  um pouco. A remoção exige contexto na janela, cabe no tempo dele (30 caracteres
  por segundo, no máximo 120), vale só na emenda — nunca no meio da fala — e
  precisa de evidência: duas palavras casando ou uma longa, com a palavra curta
  divergente valendo só encostada em vizinhas idênticas. Na dúvida, o texto fica
  como veio: um "tá" repetido é melhor do que fala comida.
- P151: **silêncio não custa mais requisição.** No S26 (2026-09-16 10:42), os 28 s
  entre o fim da fala e o toque que encerrou gastaram três chamadas que voltaram
  vazias (~14 s de áudio enviado à toa). A barreira que faltava era o corte: a
  regra da P144 só pulava janela `leading` e `flush`, e com ruído de sala parado
  a janela sai no teto e ia sempre à API. Agora a janela com menos de 80 ms de
  fala medida não é enviada (`transcription_silent_window
  reason=fala_insuficiente`), em qualquer corte. O limiar é o menor acima de zero
  que a medição permite, e fala real curta dá cinco vezes isso; janela sem
  medição vai à API, porque falta de medição não é silêncio. A janela pulada
  continua ocupando a vaga do teto de requisições (P107), e o `dictation_window`
  passou a registrar `voicedMs=`.
- P153: **o mesmo nível de áudio não é pago duas vezes.** No S26 (2026-09-16
  11:04), num ditado sussurrado, duas janelas com voz medida bem acima do limiar
  da P151 foram transcritas e voltaram vazias: o limiar de fala num quarto
  silencioso fica em 250 (−42 dBFS), que respiração e ruído de sala cruzam. Em
  vez de adivinhar um limiar novo — o jeito de quebrar quem fala baixo —, a
  sessão aprende com a resposta que já foi paga: a janela é pulada quando não é
  mais alta que alguma que já voltou vazia **e** é mais baixa que qualquer uma
  que já rendeu texto (`transcription_silent_window reason=nivel_ja_vazio`).
  Assim, o nível que já produziu texto nunca é tomado por silêncio. O
  `dictation_window` passou a registrar `peak=`, o bloco mais alto do áudio
  próprio da janela.

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
