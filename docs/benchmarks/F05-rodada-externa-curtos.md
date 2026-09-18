# F05 rodada externa — curtos 11/set (2026-09-17)

Rodada complementar à F05 in-app, rodada fora do repo com harness
`intelligent-keyboard/tools/voz/nuvem.py` (somente leitura, fora deste repo):
150 itens com gabarito (`comando` 64, `frase` 77, `fantasma` 8, `ilha` 1),
5,9 min de áudio, `language=pt`, `temperature=0`, limite US$ 1,00 aprovado
(gasto real ~US$ 0,12).

JSONs brutos arquivados fora do repo (`~/.config/FlowVoice/benchmarks/`).

> Os 3 modelos `gpt-*-transcribe` rejeitam `verbose_json` (400). Medidos à
> parte com `response_format=json` direto (mesmo corpus, mesma chave):
> sem `segments`, portanto sem sinais/mediana por categoria — WER, perfeitas,
> p50/p95 e custo comparáveis.

| Modelo | WER | Perfeitas (/141) | Comandos (/64) | Fantasmas (/9) | p50 | p95 | Custo |
|---|---|---|---|---|---|---|---|
| `microsoft/mai-transcribe-2` | **19,3%** | **108** | **49** | **0** | 736ms | 2254ms | US$ 0,0120 |
| `openai/gpt-transcribe`¹ | 19,8% | 105 | — | — | 1028ms | 1556ms | US$ 0,0325 |
| `openai/whisper-large-v3-turbo` | 23,0% | 97 | 40 | 3 | 886ms | 1997ms | **US$ 0,0012** |
| `mistralai/voxtral-mini-transcribe` | 28,8% | 93 | 39 | 3 | 800ms | 1322ms | US$ 0,0142 |
| `nvidia/parakeet-tdt-0.6b-v3` | 35,4% | 86 | 46 | 2 | **566ms** | **859ms** | US$ 0,0089 |
| `openai/gpt-4o-transcribe`¹ | 35,4% | 79 | — | — | 982ms | 1307ms | US$ 0,0172 |
| `openai/gpt-4o-mini-transcribe`¹ | 42,4% | 70 | — | — | 984ms | 1564ms | US$ 0,0083 |
| `deepgram/nova-3` | 52,3% | 78 | 32 | **0** | 623ms | 1263ms | US$ 0,0256 |

¹ `response_format=json` (sem `verbose_json`); categorias comando/fantasma não
apuradas nesse modo, só WER global.

## Leitura

- **Converge com a F05 in-app no essencial:** `mai-transcribe-2` entre os
  melhores e `nova-3` fraca em WER; turbo segue o mais barato (10×).
- **Diverge no detalhe:** aqui o `mai-transcribe-2` vence e o `gpt-transcribe`
  fica em 2º; na F05 in-app (clipe único de 17 palavras) o `gpt-transcribe`
  venceu com WER 0. Corpus maior discrimina melhor — 150 itens contra 1 clipe.
- **Fantasmas:** mai-2 e nova-3 zeram; turbo/voxtral/parakeet inventam 2–3/9.
- **Latência:** parakeet é o mais rápido (p50 566ms, p95 859ms), mas erra mais
  (35,4%). Nenhum modelo resolve latência sozinho: o piso é janela + RTT.
- **Nova-3** é a pior em WER (52,3%) apesar de 0 fantasmas — descarta junto
  com o certo.
- Rodada 2 (nemotron/qwen3-asr/chirp-3) dispensada: nada promete cobrir a
  combinação WER+fantasma do mai-2; reavaliar se preço/latência pesar no uso.
