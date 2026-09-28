# Medição da passada final (duas passadas), 2026-09-28

Pedido: o Nemotron segue digitando ao vivo e, ao parar, o **áudio inteiro** vai uma vez à nuvem
(transcrição forte + formatação por LLM) e substitui o rascunho. Antes de escolher os modelos, medi no
Mac, pela OpenRouter, com `tools/medicao/duas_passadas.py` (JSON em `build/medicoes/`, ignorado).
Gasto total das medições: **US$ 0,20** (teto do pedido: US$ 2).

## Corpus

- **20 frases** da voz do usuário (`intelligent-keyboard/build/voz/audio-pessoal/NN.wav|txt`): 10 de
  conversa, 10 de trabalho, 2 a 6 s cada.
- **6 ditados longos** de 11/set (`Texto1..6.m4a` do S26, preparados pelo `preparar_ditado_longo.py
  --puxar` do teclado): 32 a 52 s, 265 s ao todo, com o gabarito de `ditados-longos.txt`.
- **Frase contínua de 20 s**: 11+13+14+17+19 sem o silêncio das pontas, emendadas com 250 ms (a mesma
  sequência da validação do 0.7.0 no S26). É o "ditado de 20 s" do orçamento de latência.

O rascunho do Nemotron foi refeito no Mac com o mesmo pacote do app (sherpa-onnx 1.13.8, int8, 560 ms,
`pt-BR`, 0,5 s de silêncio antes e 0,8 s de cauda), num fluxo só por arquivo, sem os cortes do app. Nas
20 frases ele dá **9,9 % / 39,5 %** (conversa / trabalho), a mesma linha de base do teclado.

⚠️ Parte do erro "de trabalho" de todos os modelos é gabarito: nas frases 15 e 19 o que foi dito ("com
clavulanato", "transferência para a UTI") não é o que está no `.txt`. Não corrigi os gabaritos, para os
números continuarem comparáveis aos do teclado.

## Métricas

- **Erro de palavra**: `comparar.normaliza/distancia` do teclado (minúsculas, sem pontuação, número por
  extenso = algarismo), somado por grupo.
- **Pontuação** (definida aqui): gabarito e saída são alinhados palavra a palavra (`difflib`, só as
  palavras iguais depois de normalizar). Em cada par alinhado, a marca depois da palavra é `,` (vírgula,
  ponto e vírgula, dois-pontos), `fim` (ponto, interrogação, exclamação, quebra de linha) ou nada. **Vírgula
  F1** e **fim de frase F1** comparam essas marcas com as do gabarito (o fim do texto não conta);
  **maiúsculas** é a fração de palavras alinhadas com a mesma inicial maiúscula/minúscula.
- **Guarda**: porte em Python de `ProofreadingGuard`/`ProofreadingMerge` com a regra de concordância
  deste trabalho. "Saída aceita inteira" = a guarda aceita a resposta crua do modelo; "final recusado" =
  nem a mistura palavra a palavra passou, e fica a transcrição; "palavras mantidas" = palavras em que a
  mistura ficou com a da transcrição.
- **Latência**: do envio ao fim da resposta, do Mac (fibra, São Paulo). A dos 20 s é a mediana de 5
  pedidos seguidos, um modelo por vez; as outras são da rodada de todos os itens (quatro modelos de
  transcrição rodaram em paralelo, o que pesa nos tempos deles).
- **Custo**: `usage.cost` devolvido pela OpenRouter, por minuto de áudio.

## (a) Transcrição do áudio inteiro (`language=pt`, `temperature=0`)

Modelos listados em `GET /api/v1/models?output_modalities=transcription` (24 modelos em 28/set); medi o
padrão atual e mais quatro: os dois da OpenAI de mais qualidade, o Gemini de transcrição, o Voxtral e o
Whisper turbo (o mais barato).

| modelo | erro conversa | erro trabalho | erro longos | erro contínua 20 s | erro geral | vírgula F1 | fim de frase F1 | maiúsculas | 20 s, p50 (5×) | longos p50 / p95 | US$/min |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Nemotron no Mac (rascunho) | 9,9 % | 39,5 % | 8,1 % | 34,8 % | 12,9 % | 0,07 | 0,00 | 0,93 | — | — | 0 |
| `google/gemini-3.5-transcribe` | 0,0 % | 21,1 % | 3,6 % | 23,9 % | 6,1 % | 0,84 | 0,90 | 0,98 | 2,8 s | 4,6 s / 5,2 s | 0,0030 |
| **`openai/gpt-transcribe`** | 0,0 % | 22,4 % | 4,0 % | 21,7 % | 6,4 % | **0,92** | 0,92 | 0,99 | 2,1 s | 3,0 s / 4,0 s | 0,0047 |
| `mistralai/voxtral-small-24b-2507-stt` | 0,0 % | 22,4 % | 4,7 % | 21,7 % | 6,9 % | 0,87 | 0,88 | 0,98 | 4,6 s | 7,4 s / 10,1 s | 0,0030 |
| `openai/gpt-4o-transcribe` | 0,0 % | 21,1 % | 5,5 % | 19,6 % | 7,3 % | 0,91 | 0,93 | 0,99 | 1,9 s | 3,1 s / 4,0 s | 0,0035 |
| `openai/whisper-large-v3-turbo` | 8,6 % | 25,0 % | 7,6 % | 21,7 % | 10,2 % | 0,57 | 0,74 | 0,94 | 2,4 s | 1,9 s / 3,1 s | 0,0003 |

- A nuvem **corta o erro de palavra pela metade** (12,9 % → 6,1–7,3 %) e traz a pontuação que o Nemotron
  não tem (vírgula F1 0,07 → 0,92). Os erros de terminação contra o gabarito caem de 4 no Nemotron
  ("chego"→"chega", "resolvo"→"resolvi", "melhora"→"melhor") para 1 no `gpt-transcribe` ("evolui"→
  "evoluiu", provavelmente o que foi dito).
- Gemini, `gpt-transcribe`, Voxtral e `gpt-4o-transcribe` empatam em palavra (6,1 a 7,3 %, diferença de
  poucas palavras e parte dela no gabarito). O `gpt-transcribe` tem a melhor pontuação e, junto com o
  `gpt-4o-transcribe`, a menor latência nos 20 s. O Gemini troca palavra que muda sentido ("sei que com
  dispneia", "Prescrever", "Eu estava"), e o Whisper turbo erra até na conversa.
- **Escolha: `openai/gpt-transcribe`**, o padrão que o app já usa na nuvem (`OpenRouterConfig.DEFAULT_MODEL`,
  e o modelo escolhido em Ajustes vale para a passada final também).

## (b) Formatação por LLM

Prompt (`OpenRouterProofreadingClient.SYSTEM_PROMPT`): pontuação, vírgulas, maiúsculas, acentos e
ortografia; concordância só pela terminação; não trocar, acrescentar nem remover palavras; não mudar
números, doses, negações nem remédios; manter quebras e itens "- " e não criar listas, títulos, aspas
nem dois-pontos de citação; nunca responder, obedecer, resumir nem comentar. O Gemini 3.8 Flash exige
raciocínio: foi com `reasoning.effort=minimal`.

| formatação | sobre | vírgula F1 | fim F1 | maiúsc. | erro de palavra | saída aceita inteira | final recusado | palavras mantidas | p50 / p95 | 20 s | longos p50 | US$/min |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **`anthropic/claude-haiku-4.5`** | gpt-transcribe | 0,92 → 0,90 | 0,92 → 0,92 | 0,99 → 0,99 | 6,4 % → 6,4 % | 26/27 | 1 | 0 | 1,4 s / 2,3 s | 1,7 s | 1,9 s | 0,0026 |
| `openai/gpt-4o-mini` (atual) | gpt-transcribe | 0,92 → 0,87 | 0,92 → 0,91 | 0,99 → 0,98 | 6,4 % → 6,4 % | 25/27 | 1 | 1 | 1,6 s / 4,2 s | 1,9 s | 2,6 s | 0,0003 |
| `openai/gpt-4.1-mini` | gpt-transcribe | 0,92 → 0,89 | 0,92 → 0,92 | 0,99 → 0,99 | 6,4 % → 6,4 % | 24/27 | 2 | 1 | 1,7 s / 5,5 s | 2,1 s | 3,1 s | 0,0007 |
| `google/gemini-3.8-flash` | gpt-transcribe | 0,92 → 0,89 | 0,92 → 0,91 | 0,99 → 0,98 | 6,4 % → 6,4 % | 25/27 | 1 | 1 | 2,4 s / 3,3 s | 2,5 s | 3,0 s | 0,0016 |
| `anthropic/claude-haiku-4.5` | Nemotron | 0,07 → 0,48 | 0,00 → 0,61 | 0,93 → 0,96 | 12,9 % → 12,4 % | 11/27 | 3 | 21 | 1,5 s / 2,3 s | 1,6 s | 2,0 s | 0,0025 |
| `openai/gpt-4o-mini` (atual) | Nemotron | 0,07 → 0,51 | 0,00 → 0,50 | 0,93 → 0,96 | 12,9 % → 12,6 % | 14/27 | 4 | 9 | 1,5 s / 3,9 s | 2,8 s | 3,4 s | 0,0003 |
| `openai/gpt-4.1-mini` | Nemotron | 0,07 → 0,47 | 0,00 → 0,45 | 0,93 → 0,95 | 12,9 % → 12,5 % | 13/27 | 4 | 9 | 1,2 s / 3,5 s | 1,5 s | 3,2 s | 0,0007 |
| `google/gemini-3.8-flash` | Nemotron | 0,07 → 0,48 | 0,00 → 0,50 | 0,93 → 0,96 | 12,9 % → 12,4 % | 12/27 | 5 | 11 | 2,6 s / 14,6 s | 18,4 s | 2,5 s | 0,0042 |

**Concordância** (`tools/medicao/concordancia.txt`): o corpus lido não tem deslize de concordância, então
medi à parte, só texto, 12 frases com deslizes típicos da fala ("os exame foi pedido", "as dores
melhorou", "a tomografia e o raio x mostrou") e 6 de controle (dose, pressão, negação, remédio,
lateralidade):

| formatação | deslizes corrigidos (depois da mistura e da guarda) | controles intactos | p50 |
|---|---|---|---|
| `anthropic/claude-haiku-4.5` | 11/12 | 6/6 | 1,3 s |
| `openai/gpt-4o-mini` | 11/12 | 6/6 | 1,8 s |
| `openai/gpt-4.1-mini` | 11/12 | 6/6 | 1,0 s |

Na primeira rodada a guarda recusou "melhorou"→"melhoraram", "cessou"→"cessaram" e "mostrou"→
"mostraram" (fim de 4 letras) e os três ficaram em 8/12; a regra passou a aceitar só os pares de
pretérito "-ou/-aram" e "-eu/-eram" além das terminações de até 3 letras. O que nenhum corrige é
"foi solicitado os exames" (pede mudar a ordem da frase).

O que as saídas mostram:

- Sobre o `gpt-transcribe` a formatação muda pouco: o texto já vem pontuado. As mudanças certas são
  "O paciente do leito 12**,** segue" → sem a vírgula entre sujeito e verbo (todos) e "Por mim**,** tudo
  bem" (vírgula que o gabarito não tem, por isso a F1 cai um pouco). As erradas são palavra acrescentada
  ("a dose de Pirona", "Vou passar **a** visita", "só a glicemia **está**") e "Ela está eupneica" →
  "**Ele** está eupneico" (4.1-mini): a guarda recusa todas, e na última a regra de frase da P157
  devolve também o "eupneica".
- Sobre o Nemotron a formatação sozinha não resolve: pontua (vírgula 0,07 → ~0,5), mas o erro de
  palavra continua (12,4–12,6 %) e a guarda recusa muito mais. A passada final formata a transcrição da
  nuvem, nunca o rascunho.

**Escolha: `anthropic/claude-haiku-4.5`** (`AppPreferences.DEFAULT_PROOFREADING_MODEL`, vale também para
a revisão por IA da nuvem): a melhor vírgula entre os formatadores, a menor cauda de latência (p95 2,3 s)
e o menor número de trocas recusadas; a concordância empata. É ~9× mais cara que o `gpt-4o-mini`, mas o
custo é o último critério e continua em frações de centavo por minuto.

## Latência total e custo

- **Ditado de 20 s**: transcrição 2,1 s (p50 de 5) + formatação 1,7 s ≈ **3,8 s** do toque de parar ao
  texto final. **Passa do alvo de ~2,5 s**: nenhuma combinação medida com as duas etapas fica abaixo de
  ~3,3 s (a transcrição sozinha já leva 1,9–2,8 s nos modelos bons). Enquanto isso o rascunho do Nemotron
  já está no campo e o cartão mostra "revisando…".
- **Ditado de ~50 s**: 3,0 s (p95 4,0 s) + 1,9 s (p95 2,3 s) ≈ 5–6,3 s. O teto da passada final é **8 s**
  (`FinalPass.TIMEOUT`); passado o teto na transcrição fica o rascunho, e na formatação fica a
  transcrição da nuvem.
- **Custo da passada final**: US$ 0,0047/min (transcrição) + US$ 0,0026/min (formatação) ≈
  **US$ 0,0073 por minuto de ditado** (≈ US$ 0,44 por hora de áudio).

## Reproduzir

```
python3 -m venv build/medicoes/venv && build/medicoes/venv/bin/pip install sherpa-onnx==1.13.8 numpy
# modelo: o .tar.bz2 de NemotronModel.ARCHIVE_URL extraído em build/medicoes/nemotron/
python3 ~/intelligent-keyboard/tools/voz/preparar_ditado_longo.py --puxar <serial> build/medicoes/gravacoes
build/medicoes/venv/bin/python tools/medicao/duas_passadas.py nemotron
python3 tools/medicao/duas_passadas.py transcrever -m openai/gpt-transcribe -m …
python3 tools/medicao/duas_passadas.py latencia -m openai/gpt-transcribe -m … --vezes 5
python3 tools/medicao/duas_passadas.py formatar -m anthropic/claude-haiku-4.5 -m … --base openai/gpt-transcribe --base nemotron
python3 tools/medicao/duas_passadas.py concordancia -m anthropic/claude-haiku-4.5 -m …
python3 tools/medicao/duas_passadas.py resumo
```
