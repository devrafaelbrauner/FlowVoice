# Medição dos modelos da nuvem (OpenRouter), 2026-09-28

Pedido: manter o motor "No aparelho" e fazer um motor **totalmente na nuvem** com os modelos escolhidos em
Ajustes, escolhendo os padrões por uma medição ampla da OpenRouter com foco em **ortografia pt-BR** (acentos,
cedilha, hífen, maiúscula de nome próprio), **pontuação** (vírgulas, fim de frase, "?", ":") e **latência**.
Medido no Mac, pela OpenRouter, com `tools/medicao/duas_passadas.py` (a ferramenta das duas passadas,
estendida; JSON em `build/medicoes/nuvem/`, ignorado). Os números de 2026-09-28 de
`docs/medicao-duas-passadas.md` não foram reaproveitados: tudo foi medido de novo, um pedido por vez.

**Gasto: US$ 1,81** (uso da chave pela `GET /api/v1/key`, igual à soma de `usage.cost`), dentro do teto de
US$ 5 do pedido; a ferramenta parava sozinha em US$ 3,50.

## Escolha

| combinação | escolhido | nota | 20 s p50 / p95 | US$/min | mais rápido a até 1 ponto |
|---|---|---|---|---|---|
| transcrição sozinha | `deepgram/nova-3` | 96,2 | 1,9 s / 2,0 s | 0,0043 | o próprio (o seguinte, `gpt-4o-mini-transcribe`, faz 1,6 s com 94,7) |
| um passo (áudio → texto formatado) | `thinkingmachines/inkling` | 97,0 | 4,2 s / 5,1 s | 0,0049 | nenhum (o mais perto, `openai/gpt-audio`: 95,5; 2,6 s; US$ 0,023/min) |
| transcrição + formatação | **`deepgram/nova-3` + `openai/gpt-4.1-mini`** | 96,5 | 3,3 s / 4,5 s | 0,0050 | o próprio |
| **geral** | **`deepgram/nova-3` + `openai/gpt-4.1-mini`** (padrão do app) | 96,5 | 3,3 s / 4,5 s | 0,0050 | **`deepgram/nova-3` sem formatação**: 96,2; 1,9 s |

**Padrões do app** (valem para os dois motores; Ajustes → "Modelo de transcrição" e "Formatação"):
transcrição `deepgram/nova-3` (ao vivo e na passada final) e formatação `openai/gpt-4.1-mini`. O passo
único, quando escolhido, usa `thinkingmachines/inkling` por padrão.

Regra (a do pedido): **1º nota** (ortografia + pontuação), **2º latência** (mediana de um ditado de 20 s,
do fim do áudio ao texto final), **3º custo**. Nota a até **0,5 ponto** da melhor conta como empate: no
corpus (791 palavras, 70 vírgulas), 0,5 ponto são ~8 palavras ou ~2 vírgulas — o que uma só frase mal
ouvida muda. A "rápida" é a de menor latência com nota até 1 ponto abaixo da melhor.

- A melhor nota de todas é o **passo único do `thinkingmachines/inkling`** (97,0). Empatam com ele (≥ 96,46)
  cinco transcrições + formatação sobre o `deepgram/nova-3`: `claude-sonnet-5` (96,6; 5,5 s),
  `gemini-3.8-flash` (96,6; 4,8 s), `gemini-3.1-flash-lite` (96,5; 3,6 s), `gpt-4.1` (96,5; 3,6 s) e
  `gpt-4.1-mini` (96,5; **3,3 s**). No empate vale a latência: **`deepgram/nova-3` + `openai/gpt-4.1-mini`**
  (10 cadeias de 20 s: 3,3 s p50, 4,5 s p95), contra 4,2 s do inkling. Pesou também a segurança clínica: na
  transcrição + formatação a **guarda** confere cada palavra da formatação contra a transcrição (recusou
  2 de 27 respostas do `gpt-4.1-mini`, e a transcrição ficou), enquanto o passo único só tem a trava de
  cobertura e a de sobreposição com o rascunho (abaixo).
- **Alternativa rápida:** o próprio `deepgram/nova-3` **sem formatação** (96,2, dentro de 1 ponto do
  inkling; 1,9 s). Em Ajustes: Formatação → "Sem formatação (só a transcrição)".
- Entre as transcrições, o `deepgram/nova-3` tem a melhor nota (96,2) e a menor latência das cinco melhores
  (1,9 s nos 20 s, 1,7 s nos longos). O `openai/gpt-transcribe` (padrão até aqui) vem em 2º (95,6; 2,0 s):
  põe mais vírgula certa (F1 0,91 × 0,88), mas erra mais palavra (erro com acento 3,2 % × 2,9 %; no
  subconjunto clínico, 6,5 % × 5,1 %) e acerta menos fim de frase (0,93 × 0,97).
  O `nova-3` devolve `words` com tempos em `verbose_json` (conferido), como o app usa ao vivo.
- A formatação sobre o `nova-3` sobe a nota em ~0,3–0,4 (vírgula F1 0,88 → 0,90–0,91); sobre o
  `gpt-transcribe`, que já pontua melhor, não sobe (95,6 → 94,6–95,6). O erro de palavra não muda com
  nenhuma: o que a guarda deixa passar é pontuação, caixa, acento e concordância.

## Corpus

- **20 frases** da voz do usuário (`intelligent-keyboard/build/voz/audio-pessoal/NN.wav|txt`): 10 de
  conversa, 10 de trabalho (clínicas), 2 a 6 s cada.
- **6 ditados longos** de 11/set (`build/medicoes/gravacoes/longo-N.wav|txt`): 32 a 52 s, 265 s ao todo.
- **Contínua de 20 s**: 11+13+14+17+19 sem o silêncio das pontas, emendadas com 250 ms (22,8 s de áudio). É o
  "ditado de 20 s" das latências.
- Ao todo 27 itens, 362 s de áudio, 791 palavras de gabarito, 70 vírgulas, 54 fins de frase, 7 "?" e 1 ":".

### Gabaritos corrigidos

`tools/medicao/gabaritos-corrigidos.txt` corrige, sem mexer nos originais, os gabaritos em que **a gravação
diz outra coisa e os cinco melhores modelos de transcrição, de fornecedores diferentes, ouvem a mesma
divergência**: frases 11 ("uma radiografia de tórax", não "um raio X"), 13 ("desconforto respiratório"),
15 ("amoxicilina **com clavulanato**"), 19 ("**transferência para a** UTI **para o paciente que estava na**
enfermaria"), 20 ("depois da **analgesia adequada**"), e trechos de longo-2 ("na sala do terceiro andar",
sem o "dois"), longo-3 ("oitenta e **oito**" batimentos; "se mantiver evolução") e longo-6 ("Bom dia,
**pessoal**"). A 15 e a 19 já estavam anotadas em `docs/medicao-duas-passadas.md`; as outras apareceram
nesta conferência. A nota e as tabelas usam o gabarito corrigido; a última coluna dá a nota com o original,
que tira ~1,5–1,9 ponto de todos. Dentro de cada combinação a escolha não muda com o original; entre
combinações, o inkling ficaria 0,75 ponto à frente do `nova-3` + `gpt-4.1-mini` (fora da margem de empate).
A escolha usa o corrigido, que é o que foi dito.

## Métricas

- **Erro com acento** (ortografia, novo): distância de edição de palavras contra o gabarito, sobre as palavras
  do gabarito, com acento, cedilha e hífen **como escritos** ("esta" ≠ "está", "a" ≠ "à", "voce" ≠ "você",
  "segunda-feira" ≠ "segunda feira"), minúsculas **exceto nome próprio e sigla** do gabarito (palavra com
  maiúscula fora do começo de frase, ou toda em maiúsculas: "Mariana", "Antônio", "UTI", o "X" de "raio X"),
  que valem com a caixa escrita ("uti" ≠ "UTI"). Número por extenso = algarismo ("sete" = "7", "sessenta e
  oito" = "68"), como no erro normalizado: é convenção de escrita, não ortografia.
- **Só acento**: quantas das trocas do caminho mínimo diferem **só** em acento ou cedilha ("esta"→"está").
  As trocas só de caixa de nome próprio também são contadas (`caixa` no JSON); deram zero em todos os modelos
  da nuvem.
- **Erro normalizado** (o de sempre): `comparar.normaliza/distancia` do teclado. Na conferência, ele **já
  distinguia acento e hífen** (só tira caixa e pontuação); o erro com acento acrescenta a caixa de nome
  próprio. Por isso as duas colunas quase sempre coincidem: nenhum modelo da nuvem errou caixa de nome
  próprio, e só acento houve 0–2 trocas por modelo ("a"→"à", "quê"→"que").
- **Erro clínico**: o erro com acento só nas 10 frases de trabalho e nos longos 3 e 4 (prontuário).
- **Vírgula F1, fim de frase F1, maiúsculas**: como em `docs/medicao-duas-passadas.md` (alinhamento
  `difflib` das palavras iguais; a marca depois de cada palavra alinhada; o fim do texto não conta).
- **"?" F1 e ":" F1** (novos): se "?" (ou ":") vem depois da palavra alinhada, no gabarito e na saída; aqui o
  fim do texto conta. O corpus tem 7 "?" e 1 ":" (longo-5): o ":" é binário e fica fora da nota.
- **Nota** (0–100) = 50 × (1 − erro com acento) + 50 × média(vírgula F1, fim F1, "?" F1).
- **Latência**: do envio ao fim da resposta, do Mac (fibra, São Paulo), **um pedido por vez, um modelo por
  vez** (nada em paralelo). "20 s" é a contínua: transcrição sozinha e passo único, 5 rodadas intercaladas
  entre os modelos; transcrição + formatação, 5 **cadeias** reais (transcreve e formata em seguida) por
  formatação — 10 para `gpt-4.1-mini`, `gpt-4.1` e `gemini-3.1-flash-lite`, que empataram. "Longos" é a
  mediana e o p95 dos 6 longos (na formatação, transcrição + formatação do mesmo longo).
- **Custo**: `usage.cost` da OpenRouter por minuto de áudio (na formatação, somado ao da transcrição).
- **Guarda**: porte em Python de `ProofreadingGuard`/`ProofreadingMerge` (o do app); "recusas" = respostas em
  que nem a mistura passou e ficou a transcrição.
- **Trava do passo único**: `FinalPass.draftOverlap`, a fração das palavras do rascunho (aqui, o do Nemotron,
  o pior caso) que aparecem no texto do passo único; abaixo de 35 % o app fica com o rascunho.

Prompts: a formatação usa o do app (`OpenRouterProofreadingClient.SYSTEM_PROMPT`, texto entre `<ditado>`);
o passo único, `OpenRouterAudioChatClient.PROMPT` (transcrição fiel, palavra por palavra, com ortografia e
pontuação pt-BR, sem responder, resumir nem comentar; o áudio vai sozinho na mensagem do usuário). Transcrição
com `language=pt` e `temperature=0`; chat com `temperature=0` e o raciocínio mínimo de cada modelo
(`ESFORCO` na ferramenta, `CloudModels` no app): Gemini 3.x Flash `effort=minimal` (não aceita desligar), e
`enabled=false` no Gemini 3.1 Flash Lite / 2.5 Flash, `gpt-6-luna`, `deepseek-v4.1-flash`, `qwen3.8-flash` e
`qwen3.8-omni-flash`, que raciocinavam por padrão.

## (b) Transcrição sozinha

Todos os 24 modelos de `GET /api/v1/models?output_modalities=transcription` (28/set) aceitaram `language=pt`
e transcreveram os 27 itens sem erro.

| # | combinação | nota | erro com acento | erro normalizado | erro clínico | só acento | vírgula F1 | fim F1 | ? F1 | : F1 | maiúsc. | 20 s p50 / p95 | longos p50 / p95 | US$/min | nota (gab. original) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `deepgram/nova-3` | 96,15 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,88 | 0,97 | 1,00 | 0,00 | 1,00 | 1,9 s / 2,0 s | 1,7 s / 2,3 s | 0,0043 | 94,28 |
| 2 | `openai/gpt-transcribe` | 95,62 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,91 | 0,93 | 1,00 | 0,00 | 0,99 | 2,0 s / 2,8 s | 3,0 s / 4,4 s | 0,0047 | 94,05 |
| 3 | `google/chirp-3` | 95,32 | 4,0 % | 4,0 % | 8,3 % | 0 | 0,88 | 0,96 | 1,00 | 0,00 | 0,99 | 7,6 s / 7,7 s | 12,4 s / 14,6 s | 0,0166 | 93,63 |
| 4 | `openai/gpt-4o-transcribe` | 95,32 | 3,9 % | 3,9 % | 7,2 % | 0 | 0,91 | 0,93 | 1,00 | 0,00 | 0,99 | 2,1 s / 2,6 s | 3,6 s / 4,9 s | 0,0035 | 93,74 |
| 5 | `microsoft/mai-transcribe-2` | 95,18 | 2,8 % | 2,8 % | 4,7 % | 0 | 0,87 | 0,92 | 1,00 | 0,00 | 0,99 | 2,0 s / 2,3 s | 1,5 s / 2,2 s | 0,0017 | 93,29 |
| 6 | `x-ai/grok-stt-1.0` | 94,99 | 5,8 % | 5,8 % | 12,3 % | 0 | 0,92 | 0,95 | 1,00 | 0,00 | 0,99 | 1,9 s / 2,7 s | 2,4 s / 3,5 s | 0,0017 | 93,16 |
| 7 | `qwen/qwen3-asr-flash-2026-02-10` | 94,91 | 2,9 % | 2,9 % | 6,1 % | 0 | 0,88 | 0,90 | 1,00 | 0,50 | 0,98 | 2,3 s / 2,9 s | 4,5 s / 6,1 s | 0,0020 | 93,31 |
| 8 | `openai/gpt-4o-mini-transcribe` | 94,72 | 3,0 % | 3,0 % | 5,1 % | 0 | 0,85 | 0,92 | 1,00 | 0,00 | 0,99 | 1,6 s / 2,9 s | 2,8 s / 3,4 s | 0,0018 | 93,26 |
| 9 | `microsoft/mai-transcribe-1.5` | 94,28 | 4,2 % | 4,2 % | 8,3 % | 0 | 0,87 | 0,91 | 1,00 | 0,00 | 0,99 | 2,1 s / 3,4 s | 2,1 s / 2,7 s | 0,0062 | 92,39 |
| 10 | `mistralai/voxtral-small-24b-2507-stt` | 94,05 | 3,9 % | 3,9 % | 8,3 % | 0 | 0,87 | 0,89 | 1,00 | 0,00 | 0,99 | 5,1 s / 6,2 s | 9,3 s / 9,6 s | 0,0030 | 92,15 |
| 11 | `google/gemini-3.5-transcribe` | 93,76 | 3,3 % | 3,3 % | 6,9 % | 1 | 0,83 | 0,90 | 1,00 | 0,00 | 0,99 | 3,4 s / 3,7 s | 3,7 s / 4,0 s | 0,0030 | 92,42 |
| 12 | `qwen/qwen3-asr-1.7b` | 92,51 | 4,8 % | 4,8 % | 9,4 % | 2 | 0,83 | 0,87 | 1,00 | 0,00 | 0,97 | 2,6 s / 4,8 s | 2,2 s / 3,0 s | 0,0005 | 91,29 |
| 13 | `mistralai/voxtral-mini-transcribe` | 92,19 | 5,3 % | 5,3 % | 9,7 % | 0 | 0,80 | 0,89 | 1,00 | 0,00 | 0,98 | 1,9 s / 59,3 s | 2,7 s / 3,4 s | 0,0029 | 90,61 |
| 14 | `fish-audio/transcribe-1` | 92,09 | 5,7 % | 5,7 % | 9,4 % | 3 | 0,81 | 0,89 | 1,00 | 0,00 | 0,98 | 1,7 s / 3,5 s | 2,6 s / 2,9 s | 0,0062 | 90,88 |
| 15 | `assemblyai/universal-3-5-pro` | 91,49 | 3,5 % | 3,5 % | 7,2 % | 0 | 0,77 | 0,91 | 0,92 | 0,67 | 0,98 | 2,7 s / 3,3 s | 2,3 s / 3,6 s | 0,0037 | 89,81 |
| 16 | `mistralai/voxtral-mini-3b-2507` | 91,04 | 5,9 % | 5,9 % | 11,6 % | 0 | 0,77 | 0,87 | 1,00 | 0,00 | 0,98 | 2,3 s / 2,8 s | 2,6 s / 4,5 s | 0,0010 | 89,28 |
| 17 | `fish-audio/transcribe-1-pro` | 89,18 | 12,6 % | 12,6 % | 17,0 % | 2 | 0,87 | 0,93 | 0,92 | 0,00 | 0,99 | 1,6 s / 2,3 s | 2,8 s / 3,3 s | 0,0062 | 87,56 |
| 18 | `meta/muse-voice-transcribe-1.0` | 88,78 | 5,1 % | 5,1 % | 8,7 % | 0 | 0,64 | 0,83 | 1,00 | 0,00 | 0,97 | 7,3 s / 9,0 s | 10,9 s / 15,0 s | 0,0030 | 87,33 |
| 19 | `openai/whisper-1` | 87,89 | 6,8 % | 6,8 % | 11,9 % | 2 | 0,73 | 0,74 | 1,00 | 0,00 | 0,97 | 3,0 s / 3,5 s | 3,8 s / 5,8 s | 0,0062 | 85,86 |
| 20 | `nvidia/parakeet-tdt-0.6b-v3` | 87,10 | 9,0 % | 9,0 % | 15,2 % | 1 | 0,67 | 0,82 | 1,00 | 0,00 | 0,97 | 1,5 s / 2,3 s | 1,7 s / 2,3 s | 0,0015 | 85,42 |
| 21 | `qwen/qwen3-asr-0.6b` | 82,95 | 10,4 % | 10,2 % | 15,2 % | 1 | 0,68 | 0,78 | 0,83 | 1,00 | 0,96 | 1,8 s / 2,4 s | 1,9 s / 3,4 s | 0,0002 | 81,22 |
| 22 | `openai/whisper-large-v3-turbo` | 82,57 | 8,6 % | 8,6 % | 15,9 % | 2 | 0,59 | 0,76 | 0,86 | 0,00 | 0,95 | 3,6 s / 10,4 s | 2,9 s / 3,5 s | 0,0002 | 80,92 |
| 23 | `openai/whisper-large-v3` | 81,61 | 8,8 % | 8,8 % | 16,6 % | 0 | 0,60 | 0,73 | 0,83 | 0,00 | 0,96 | 7,1 s / 9,4 s | 5,2 s / 29,7 s | 0,0009 | 79,94 |
| 24 | `nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b` | 44,88 | 10,2 % | 10,2 % | 18,1 % | 2 | 0,00 | 0,00 | 0,00 | 0,00 | 0,92 | 2,0 s / 3,3 s | 3,1 s / 3,7 s | 0,0002 | 43,49 |

Rascunho do Nemotron (motor do aparelho, refeito no Mac como em `docs/medicao-duas-passadas.md`), para
referência:

| # | combinação | nota | erro com acento | erro normalizado | erro clínico | só acento | vírgula F1 | fim F1 | ? F1 | : F1 | maiúsc. | 20 s p50 / p95 | longos p50 / p95 | US$/min | nota (gab. original) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `Nemotron no Mac (rascunho)` | 46,02 | 10,1 % | 10,1 % | 16,6 % | 1 | 0,06 | 0,00 | 0,00 | 0,00 | 0,93 | — / — | — / — | 0 | 44,64 |

- O `deepgram/nova-3` escreve número por extenso ("sete às treze horas") e erra termo clínico raro no longo 3
  ("febril" por "afebril", "trombicina" por "azitromicina") — a nota conta cada um como uma palavra. O
  `gpt-transcribe` escreve algarismo e erra menos termo raro nesse longo, mas pontua pior as frases curtas.
- `google/chirp-3` empata em acerto (95,3), mas leva 7,6 s nos 20 s e custa US$ 0,0166/min.
- Os Whisper e os modelos pequenos (Qwen 0.6B, Parakeet, Nemotron streaming) ficam 8 a 51 pontos abaixo; o
  Nemotron streaming da OpenRouter não pontua nada.

## (c) Um passo só (áudio → texto formatado)

Os 23 modelos de chat com `audio` em `input_modalities` (sem as versões `:batch`, os aliases `~…-latest`, os
roteadores `openrouter/auto*` e as cópias `-preview`/`-customtools` do mesmo modelo). Um modelo parava no
primeiro item acima de US$ 0,05 por minuto ou depois de 3 falhas.

| # | combinação | nota | erro com acento | erro normalizado | erro clínico | só acento | vírgula F1 | fim F1 | ? F1 | : F1 | maiúsc. | 20 s p50 / p95 | longos p50 / p95 | US$/min | nota (gab. original) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `thinkingmachines/inkling` | 96,96 | 2,9 % | 2,9 % | 5,8 % | 0 | 0,94 | 0,96 | 1,00 | 1,00 | 0,99 | 4,2 s / 5,1 s | 5,5 s / 7,8 s | 0,0049 | 95,35 |
| 2 | `openai/gpt-audio` | 95,54 | 3,5 % | 3,5 % | 6,1 % | 0 | 0,91 | 0,93 | 1,00 | 0,67 | 0,99 | 2,6 s / 3,7 s | 3,7 s / 4,5 s | 0,0228 | 93,78 |
| 3 | `google/gemini-3.5-flash` | 94,81 | 2,5 % | 2,5 % | 5,1 % | 0 | 0,86 | 0,91 | 1,00 | 0,00 | 0,98 | 3,1 s / 6,2 s | 3,9 s / 6,0 s | 0,0073 | 93,36 |
| 4 | `google/gemini-3.1-flash-lite` | 94,48 | 2,5 % | 2,5 % | 4,7 % | 0 | 0,84 | 0,91 | 1,00 | 0,00 | 0,99 | 2,6 s / 3,6 s | 3,3 s / 4,5 s | 0,0012 | 92,89 |
| 5 | `google/gemini-3-flash-preview` | 94,45 | 2,8 % | 2,8 % | 5,1 % | 0 | 0,84 | 0,91 | 1,00 | 0,00 | 0,99 | 3,3 s / 3,7 s | 3,7 s / 4,3 s | 0,0024 | 92,56 |
| 6 | `google/gemini-3.5-flash-lite` | 94,36 | 3,9 % | 3,9 % | 8,7 % | 0 | 0,90 | 0,94 | 0,93 | 1,00 | 0,99 | 3,0 s / 3,1 s | 3,1 s / 4,2 s | 0,0011 | 92,61 |
| 7 | `openai/gpt-audio-mini` (trava 1/27) | 94,09 | 4,2 % | 4,2 % | 6,5 % | 0 | 0,87 | 0,90 | 1,00 | 0,50 | 0,99 | 2,3 s / 3,6 s | 3,1 s / 4,0 s | 0,0012 | 92,45 |
| 8 | `google/gemini-3.8-flash` | 93,29 | 3,7 % | 3,7 % | 5,8 % | 2 | 0,85 | 0,92 | 0,93 | 0,00 | 0,99 | 4,0 s / 4,6 s | 4,9 s / 6,1 s | 0,0023 | 91,39 |
| 9 | `google/gemini-3.7-flash` | 92,97 | 3,7 % | 3,7 % | 6,5 % | 0 | 0,86 | 0,90 | 0,93 | 0,25 | 0,99 | 3,8 s / 4,1 s | 4,0 s / 5,4 s | 0,0024 | 91,33 |
| 10 | `google/gemini-3.6-flash` | 92,93 | 3,4 % | 3,4 % | 6,1 % | 1 | 0,84 | 0,90 | 0,93 | 0,67 | 0,99 | 3,5 s / 4,6 s | 4,0 s / 5,2 s | 0,0024 | 91,02 |
| 11 | `qwen/qwen3.8-omni-flash` | 92,26 | 1,9 % | 1,9 % | 4,0 % | 0 | 0,82 | 0,94 | 0,83 | 1,00 | 0,99 | 7,3 s / 37,0 s | 8,2 s / 10,5 s | 0,0003 | 90,62 |
| 12 | `google/gemini-2.5-flash-lite` | 92,12 | 6,3 % | 6,3 % | 13,0 % | 0 | 0,87 | 0,93 | 0,92 | 0,00 | 0,99 | 3,3 s / 3,4 s | 3,9 s / 4,3 s | 0,0006 | 90,39 |
| 13 | `google/gemini-2.5-flash` | 91,74 | 4,9 % | 4,9 % | 10,8 % | 0 | 0,83 | 0,90 | 0,92 | 0,00 | 0,98 | 3,7 s / 4,5 s | 3,5 s / 4,9 s | 0,0023 | 89,96 |
| 14 | `xiaomi/mimo-v2.6-pro` | 88,75 | 10,6 % | 10,6 % | 10,5 % | 0 | 0,85 | 0,89 | 0,91 | 1,00 | 0,99 | 10,4 s / 39,3 s | 9,6 s / 24,8 s | 0,0005 | 87,30 |
| 15 | `xiaomi/mimo-v2.6-pro-ultraspeed` | 88,60 | 11,8 % | 11,8 % | 12,3 % | 0 | 0,86 | 0,90 | 0,91 | 1,00 | 0,99 | 3,6 s / 7,1 s | 4,7 s / 5,7 s | 0,0049 | 87,16 |
| 16 | `xiaomi/mimo-v2.6-flash` (trava 1/27) | 87,95 | 13,4 % | 13,4 % | 14,1 % | 0 | 0,83 | 0,93 | 0,92 | 0,00 | 0,99 | 6,7 s / 16,4 s | 8,1 s / 18,0 s | 0,0002 | 86,69 |
| 17 | `perceptron/perceptron-mk1.5` (trava 1/27) | 87,85 | 10,4 % | 10,4 % | 19,1 % | 1 | 0,77 | 0,89 | 0,92 | 0,00 | 0,99 | 5,7 s / 6,2 s | 9,5 s / 10,8 s | 0,0006 | 86,11 |
| 18 | `google/gemini-2.5-pro` | — | 0,0 % | 0,0 % | — | 0 | 0,00 | 0,50 | 1,00 | — | 0,89 | — / — | — / — | 0,1532 | — |
| 19 | `google/gemini-3.1-pro-preview` | — | 0,0 % | 0,0 % | — | 0 | 1,00 | 1,00 | 1,00 | — | 1,00 | — / — | — / — | 0,0925 | — |
| 20 | `mistralai/voxtral-small-24b-2507` | — | 3,1 % | 3,1 % | 6,1 % | 0 | 0,62 | 0,55 | 1,00 | — | 0,96 | — / — | — / — | 0,0054 | — |
| 21 | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` (trava 9/9) | — | 100,0 % | 100,0 % | — | 0 | — | — | — | — | — | — / — | — / — | 0,0000 | — |
| 22 | `xiaomi/mimo-v2.5` | — | 5,6 % | 5,6 % | — | 0 | 0,67 | — | — | — | 0,94 | — / — | — / — | 0,0005 | — |

Fora da comparação (sem nota): `google/gemini-2.5-pro` (US$ 0,15/min, raciocínio obrigatório) e
`google/gemini-3.1-pro-preview` (US$ 0,09/min, ~10 s numa frase) pararam no teto de preço; o
`mistralai/voxtral-small-24b-2507` de chat teve 429 do provedor em todos os longos, nas duas tentativas;
`thinkingmachines/inkling-small`, `xiaomi/mimo-v2.5` e `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`
devolveram resposta vazia.

- **Responder em vez de transcrever:** o `openai/gpt-audio-mini` respondeu "Claro, vou avisar assim que
  chegar." à frase 10 ("Me avisa quando chegar, por favor."). Do mesmo tamanho, isso passaria pela trava de
  cobertura; a trava de sobreposição (17 % das palavras do rascunho) recusa. Os modelos que transcrevem
  ficaram com ao menos 44 % das palavras até contra o rascunho ruim do Nemotron. O `gpt-audio-mini` fica
  fora da lista do app; os outros cinco listados (inkling, `gpt-audio`, `gemini-3.5-flash`,
  `gemini-3.1-flash-lite`, `gemini-3-flash-preview`) nunca responderam.
- O inkling acerta mais vírgula que qualquer transcrição (F1 0,94) e é o único com o ":" do longo-5, mas nos
  longos chega a 7,8 s (p95), perto do teto de 8 s da passada final.

## (d) Transcrição + formatação

Sobre as duas melhores transcrições (`deepgram/nova-3` e `openai/gpt-transcribe`), 15 formatações das
famílias pedidas: Anthropic (`claude-haiku-4.5`, o padrão até aqui, e `claude-sonnet-5`), OpenAI
(`gpt-4o-mini`, o antigo, `gpt-4.1-mini`, `gpt-4.1`, `gpt-6-luna`), Google (`gemini-3.8-flash`,
`gemini-3.5-flash-lite`, `gemini-3.1-flash-lite`), Mistral (`mistral-medium-3.1`, `mistral-small-3.2-24b`),
Meta (`llama-4-maverick`), DeepSeek (`deepseek-v4.1-flash`) e Qwen (`qwen3.8-flash`,
`qwen3-235b-a22b-2507`). Mesmo prompt e mesma guarda do app. Na sondagem ficaram de fora
`moonshotai/kimi-k2-0905` (raciocinou 2.860 tokens, 78 s numa frase) e `x-ai/grok-4.7` (raciocínio
obrigatório). O `qwen3-235b-a22b-2507` teve erro 400 do provedor em 3 dos primeiros itens e parou.

| # | combinação | nota | erro com acento | erro normalizado | erro clínico | só acento | vírgula F1 | fim F1 | ? F1 | : F1 | maiúsc. | 20 s p50 / p95 | longos p50 / p95 | US$/min | nota (gab. original) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `deepgram/nova-3 + anthropic/claude-sonnet-5` (recusas 1/27) | 96,58 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,91 | 0,97 | 1,00 | 0,00 | 1,00 | 5,5 s / 6,8 s | 5,4 s / 19,0 s | 0,0130 | 94,73 |
| 2 | `deepgram/nova-3 + google/gemini-3.8-flash` (recusas 1/27) | 96,56 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,91 | 0,97 | 1,00 | 0,00 | 1,00 | 4,8 s / 6,2 s | 4,1 s / 5,5 s | 0,0060 | 94,71 |
| 3 | `deepgram/nova-3 + google/gemini-3.1-flash-lite` (recusas 0/27) | 96,48 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,6 s / 4,6 s | 3,9 s / 4,4 s | 0,0049 | 94,62 |
| 4 | `deepgram/nova-3 + openai/gpt-4.1` (recusas 1/27) | 96,48 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,6 s / 5,6 s | 4,4 s / 5,4 s | 0,0080 | 94,62 |
| 5 | `deepgram/nova-3 + openai/gpt-4.1-mini` (recusas 2/27) | 96,46 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,3 s / 4,5 s | 4,5 s / 6,3 s | 0,0050 | 94,60 |
| 6 | `deepgram/nova-3 + anthropic/claude-haiku-4.5` (recusas 1/27) | 96,44 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,5 s / 5,0 s | 3,6 s / 5,7 s | 0,0069 | 94,58 |
| 7 | `deepgram/nova-3 + google/gemini-3.5-flash-lite` (recusas 1/27) | 96,41 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,6 s / 4,4 s | 3,5 s / 4,1 s | 0,0051 | 94,55 |
| 8 | `deepgram/nova-3 + mistralai/mistral-small-3.2-24b-instruct` (recusas 0/27) | 96,23 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,90 | 0,96 | 1,00 | 0,00 | 0,99 | 4,0 s / 5,6 s | 4,4 s / 9,0 s | 0,0045 | 94,64 |
| 9 | `deepgram/nova-3 + deepseek/deepseek-v4.1-flash` (recusas 1/27) | 96,23 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,89 | 0,97 | 1,00 | 0,00 | 1,00 | 15,1 s / 17,3 s | 4,4 s / 5,4 s | 0,0046 | 94,36 |
| 10 | `deepgram/nova-3 + openai/gpt-4o-mini` (recusas 2/27) | 96,18 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,90 | 0,96 | 1,00 | 0,00 | 0,99 | 5,1 s / 5,8 s | 4,8 s / 6,4 s | 0,0046 | 94,32 |
| 11 | `deepgram/nova-3 + meta-llama/llama-4-maverick` (recusas 1/27) | 96,12 | 3,0 % | 3,0 % | 5,1 % | 1 | 0,90 | 0,96 | 1,00 | 0,00 | 0,99 | 11,1 s / 14,3 s | 17,2 s / 22,6 s | 0,0046 | 94,54 |
| 12 | `deepgram/nova-3 + qwen/qwen3.8-flash` (recusas 0/27) | 95,90 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,89 | 0,95 | 1,00 | 0,00 | 0,99 | 5,6 s / 6,6 s | 5,1 s / 7,5 s | 0,0046 | 94,32 |
| 13 | `deepgram/nova-3 + openai/gpt-6-luna` (recusas 1/27) | 95,80 | 2,9 % | 2,9 % | 5,1 % | 1 | 0,88 | 0,95 | 1,00 | 0,00 | 0,99 | 3,8 s / 5,6 s | 3,7 s / 4,8 s | 0,0045 | 93,94 |
| 14 | `openai/gpt-transcribe + google/gemini-3.5-flash-lite` (recusas 1/27) | 95,62 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,91 | 0,93 | 1,00 | 0,00 | 0,99 | 4,2 s / 4,4 s | 5,0 s / 6,5 s | 0,0055 | 94,05 |
| 15 | `openai/gpt-transcribe + anthropic/claude-haiku-4.5` (recusas 1/27) | 95,36 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,6 s / 4,9 s | 4,6 s / 6,3 s | 0,0073 | 93,79 |
| 16 | `openai/gpt-transcribe + anthropic/claude-sonnet-5` (recusas 1/27) | 95,36 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 5,1 s / 5,7 s | 7,1 s / 9,2 s | 0,0113 | 93,79 |
| 17 | `openai/gpt-transcribe + deepseek/deepseek-v4.1-flash` (recusas 0/27) | 95,36 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,6 s / 6,1 s | 5,8 s / 12,3 s | 0,0050 | 93,79 |
| 18 | `openai/gpt-transcribe + google/gemini-3.1-flash-lite` (recusas 1/27) | 95,36 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,0 s / 6,4 s | 5,1 s / 6,8 s | 0,0052 | 93,79 |
| 19 | `openai/gpt-transcribe + google/gemini-3.8-flash` (recusas 1/27) | 95,36 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,2 s / 6,1 s | 5,9 s / 7,4 s | 0,0064 | 93,79 |
| 20 | `openai/gpt-transcribe + qwen/qwen3.8-flash` (recusas 0/27) | 95,36 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,7 s / 6,0 s | 6,5 s / 10,2 s | 0,0049 | 93,79 |
| 21 | `openai/gpt-transcribe + openai/gpt-4.1` (recusas 1/27) | 95,18 | 3,3 % | 3,3 % | 6,5 % | 0 | 0,88 | 0,93 | 1,00 | 0,00 | 0,99 | 3,8 s / 4,5 s | 5,0 s / 6,8 s | 0,0084 | 93,60 |
| 22 | `openai/gpt-transcribe + openai/gpt-4.1-mini` (recusas 2/27) | 95,15 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,88 | 0,93 | 1,00 | 0,00 | 0,99 | 5,2 s / 12,2 s | 5,2 s / 7,1 s | 0,0054 | 93,57 |
| 23 | `openai/gpt-transcribe + mistralai/mistral-small-3.2-24b-instruct` (recusas 2/27) | 95,09 | 3,3 % | 3,3 % | 6,5 % | 0 | 0,88 | 0,93 | 1,00 | 0,00 | 0,99 | 5,0 s / 7,3 s | 10,6 s / 15,9 s | 0,0048 | 93,51 |
| 24 | `openai/gpt-transcribe + mistralai/mistral-medium-3.1` (recusas 4/27) | 94,87 | 3,4 % | 3,4 % | 6,9 % | 0 | 0,87 | 0,93 | 1,00 | 0,00 | 0,99 | 4,0 s / 6,0 s | 4,7 s / 6,8 s | 0,0055 | 93,29 |
| 25 | `openai/gpt-transcribe + meta-llama/llama-4-maverick` (recusas 2/27) | 94,78 | 3,3 % | 3,3 % | 6,5 % | 0 | 0,86 | 0,93 | 1,00 | 0,00 | 0,99 | 9,5 s / 11,8 s | 22,5 s / 27,0 s | 0,0050 | 93,20 |
| 26 | `openai/gpt-transcribe + openai/gpt-4o-mini` (recusas 1/27) | 94,70 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,86 | 0,92 | 1,00 | 0,00 | 0,99 | 4,6 s / 4,9 s | 6,7 s / 8,5 s | 0,0049 | 93,12 |
| 27 | `openai/gpt-transcribe + openai/gpt-6-luna` (recusas 0/27) | 94,58 | 3,2 % | 3,2 % | 6,5 % | 0 | 0,85 | 0,92 | 1,00 | 0,00 | 0,99 | 4,2 s / 4,5 s | 5,4 s / 7,3 s | 0,0049 | 93,00 |
| 28 | `deepgram/nova-3 + mistralai/mistral-medium-3.1` (recusas 0/27) | 94,06 | 2,9 % | 2,9 % | 5,1 % | 0 | 0,83 | 0,91 | 1,00 | 0,00 | 0,99 | 3,7 s / 4,9 s | 3,6 s / 4,3 s | 0,0052 | 92,17 |
| 29 | `deepgram/nova-3 + qwen/qwen3-235b-a22b-2507` (recusas 0/12) | — | 3,9 % | 3,9 % | 10,0 % | 0 | 0,78 | 0,86 | 1,00 | — | 0,99 | 4,2 s / 4,5 s | — / — | 0,0050 | — |
| 30 | `openai/gpt-transcribe + qwen/qwen3-235b-a22b-2507` (recusas 2/10) | — | 6,0 % | 6,0 % | 15,0 % | 0 | 0,73 | 0,57 | 1,00 | — | 0,95 | 4,3 s / 6,3 s | — / — | 0,0060 | — |

- Sobre o `nova-3`, as sete primeiras ficam entre 96,4 e 96,6; a diferença entre elas é de 1–2 vírgulas. A
  latência separa: `gpt-4.1-mini` 3,3 s, `claude-haiku-4.5` 3,5 s, `gemini-3.1-flash-lite` 3,6 s,
  `gpt-4.1` 3,6 s, `gemini-3.8-flash` 4,8 s, `claude-sonnet-5` 5,5 s. `deepseek-v4.1-flash` (15 s) e
  `llama-4-maverick` (11 s) tiveram fila no provedor.
- `mistral-medium-3.1` sobre o `nova-3` tira vírgulas certas (F1 0,83) e cai 2 pontos.

**Concordância** (`tools/medicao/concordancia.txt`, só texto: 12 frases com deslize de concordância da fala e 6
de controle — dose, pressão, negação, remédio, lateralidade), depois da mistura e da guarda:

| formatação | deslizes corrigidos | controles intactos | p50 |
|---|---|---|---|
| `anthropic/claude-sonnet-5` | 12/12 | 6/6 | 2,4 s |
| `openai/gpt-4.1` | 12/12 | 6/6 | 1,2 s |
| `openai/gpt-6-luna` | 12/12 | 6/6 | 1,4 s |
| `google/gemini-3.8-flash` | 12/12 | 6/6 | 2,6 s |
| `meta-llama/llama-4-maverick` | 12/12 | 6/6 | 1,9 s |
| **`openai/gpt-4.1-mini`** | 11/12 | 6/6 | 1,2 s |
| `anthropic/claude-haiku-4.5` | 11/12 | 6/6 | 1,3 s |
| `openai/gpt-4o-mini` | 11/12 | 6/6 | 1,6 s |
| `google/gemini-3.5-flash-lite` | 11/12 | 6/6 | 1,5 s |
| `google/gemini-3.1-flash-lite` | 11/12 | 6/6 | 1,7 s |
| `mistralai/mistral-medium-3.1` | 11/12 | 6/6 | 1,1 s |
| `deepseek/deepseek-v4.1-flash` | 11/12 | 6/6 | 1,1 s |
| `mistralai/mistral-small-3.2-24b-instruct` | 10/12 | 6/6 | 1,8 s |
| `qwen/qwen3.8-flash` | 9/12 | 6/6 | 1,6 s |

Nenhuma formatação mexeu num controle. O que o `gpt-4.1-mini` não corrige é "foi solicitado os exames" (pede
mudar a ordem da frase), como os de 11/12.

## Latência total e custo com os padrões

- **Ditado de 20 s**: transcrição `nova-3` (~1,9 s) + formatação `gpt-4.1-mini` (~1,5 s): **3,3 s** p50, 4,5 s
  p95, do Mac — ~0,5 s mais rápido que o `gpt-transcribe` + `claude-haiku-4.5` de antes (4,6 s nesta rodada).
  Ainda acima do alvo de ~2,5 s (DP-latencia); abaixo dele só a transcrição sozinha (1,9 s).
- **Longos (32–52 s)**: 4,5 s p50, 6,3 s p95, dentro do teto de 8 s (`FinalPass.TIMEOUT`).
- **Custo da passada final**: US$ 0,0043/min (transcrição) + US$ 0,0007/min (formatação) ≈ **US$ 0,0050 por
  minuto** de ditado (antes US$ 0,0073). No motor Nuvem o ao vivo soma US$ 0,0043/min de áudio mais o contexto
  repetido das janelas (`DictationCostEstimate`).

## Reproduzir

```
export PYTHONDONTWRITEBYTECODE=1
M="python3 tools/medicao/duas_passadas.py --pasta nuvem --limite 3.5"
# uso-inicial.json na pasta: {"usage": <usage da chave em GET /api/v1/key>} (o teto conta a partir dele)
$M transcrever --todos
$M latencia -m <cada transcrição completa> --vezes 5
$M um-passo -m google/gemini-3.8-flash -m … -m thinkingmachines/inkling
$M latencia --tipo um-passo -m <cada passo único completo> --vezes 5
$M formatar -m anthropic/claude-haiku-4.5 -m … --base deepgram/nova-3 --base openai/gpt-transcribe
$M cadeia -t deepgram/nova-3 -f anthropic/claude-haiku-4.5 -f … --vezes 5
$M concordancia -m anthropic/claude-haiku-4.5 -m …
$M resumo      # build/medicoes/nuvem/resumo.json e tabela.md
```

O rascunho do Nemotron vem de `duas_passadas.py nemotron` (ver `docs/medicao-duas-passadas.md`).
