# F05 rodada 1 — resultados (curtos 11/set, 150 itens, 5,9 min)

Data: 2026-09-17. Harness: `intelligent-keyboard/tools/voz/nuvem.py`
(`language=pt`, `temperature=0`, `verbose_json` onde suportado).
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

- **Precisão:** `mai-transcribe-2` vence (19,3%, 108 perfeitas, 0 fantasmas),
  seguido de perto por `gpt-transcribe` (19,8%). Turbo fica em 3º (23,0%).
- **Custo:** turbo é 10× mais barato que o 2º (US$ 0,0002/min vs 0,0017/min).
- **Latência:** parakeet é o mais rápido (p50 566ms, p95 859ms), mas erra mais
  (35,4%). Nenhum modelo resolve latência sozinho: o piso é janela + RTT.
- **Fantasmas:** mai-2 e nova-3 zeram; turbo/voxtral/parakeet inventam 2–3/9.
- **Nova-3** é o pior em WER (52,3%) apesar de 0 fantasmas — descarta junto
  com o certo.

## Decisão

- **Padrão:** `microsoft/mai-transcribe-2` — melhor WER, 0 fantasmas, p50 <1s.
- **Fallback:** `openai/whisper-large-v3-turbo` — 3º em WER, 10× mais barato,
  já validado E2E no aparelho (F04.7).
- Rodada 2 (nemotron/qwen/chirp) dispensada: nenhum promete cobrir a
  combinação WER+f antasma do mai-2; reavaliar se o preço/latência do mai-2
  pesar no uso real.

Custo total da rodada: ~US$ 0,12 (dentro do limite US$ 1,00 aprovado).
