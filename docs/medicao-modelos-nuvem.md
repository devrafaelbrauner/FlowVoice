# Medição dos modelos da nuvem (OpenRouter), 2026-09-28

Pedido: manter o motor "No aparelho" e fazer um motor **totalmente na nuvem** com os modelos escolhidos em
Ajustes, escolhendo os padrões por uma medição ampla da OpenRouter com foco em **ortografia pt-BR** (acentos,
cedilha, hífen, maiúscula de nome próprio), **pontuação** (vírgulas, fim de frase, "?", ":") e **latência**.
Medido no Mac, pela OpenRouter, com `tools/medicao/duas_passadas.py` (a ferramenta das duas passadas,
estendida; JSON em `build/medicoes/nuvem/`, ignorado). Os números de 2026-09-28 de
`docs/medicao-duas-passadas.md` não foram reaproveitados: tudo foi medido de novo, um pedido por vez.

**Gasto: US$ 1,81** (a revisão da métrica foi refeita sem pedido novo, sobre os JSON medidos) (uso da chave pela `GET /api/v1/key`, igual à soma de `usage.cost`), dentro do teto de
US$ 5 do pedido; a ferramenta parava sozinha em US$ 3,50.

## Escolha

**Revisão de 2026-09-28 (tarde):** a primeira escolha (`deepgram/nova-3` + `openai/gpt-4.1-mini`) usava uma
normalização que contava "cento e 20" × "120", "oitocentos" × "800" e "Sra." × "senhora" como erro de
palavra e punha na média, com o mesmo peso de uma vírgula, erros que mudam o sentido — o `nova-3` escreveu
"febril" onde foi dito "afebril". Com a normalização da tabela abaixo e a regra nova, o padrão mudou.

Regra: **(1) qualquer erro que muda sentido desclassifica o modelo como padrão** (se todos tivessem, venceria
o que tem menos); **(2) nota** (ortografia + pontuação), com nota a até **0,5 ponto** da melhor contando como
empate (no corpus, ~8 palavras ou ~2 vírgulas); **(3) latência** (mediana de um ditado de 20 s, do fim do áudio
ao texto final); **(4) custo**. A "rápida" é a de menor latência com nota até 1 ponto abaixo da escolhida,
entre as sem erro de sentido. A escolha é a mesma com o gabarito corrigido e com o original (com o original
todos ganham os mesmos 5 "erros" das frases em que o gabarito diverge da gravação).

| combinação | escolhido | erros de sentido | nota | 20 s p50 / p95 | US$/min | rápida (a até 1 ponto, sem erro de sentido) |
|---|---|---|---|---|---|---|
| transcrição sozinha | `openai/gpt-4o-mini-transcribe` | 0 | 95,8 (empata com `google/chirp-3`, 96,3, que leva 7,6 s) | 1,6 s / 2,9 s | 0,0018 | o próprio |
| um passo (áudio → texto formatado) | **`thinkingmachines/inkling`** | 0 | **97,7** | 4,2 s / 5,1 s | 0,0049 | o próprio (o seguinte sem erro, `gemini-3.5-flash`, 95,7; 3,1 s) |
| transcrição + formatação | `openai/gpt-transcribe` + `openai/gpt-4.1` | 1 (todas têm ≥ 1) | 96,1 | 3,8 s / 4,5 s | 0,0084 | o próprio |
| **geral (padrão do app)** | **`thinkingmachines/inkling`, um passo** | 0 | 97,7 | 4,2 s / 5,1 s | 0,0049 | — (nenhuma outra sem erro a até 1 ponto) |

**Padrões do app:** passada final (nos dois motores) = **um passo só com `thinkingmachines/inkling`**
(`AppPreferences.formattingMode = OneStep`, teto de 12 s: os longos chegam a 7,8 s p95); modelo de
transcrição = **`openai/gpt-4o-mini-transcribe`** (as janelas ao vivo do motor Nuvem, e a passada final
quando se escolhe formatação por LLM ou nenhuma); formatação por LLM, quando escolhida = **`openai/gpt-4.1`**.

### Antes e depois da correção da métrica (gabarito corrigido)

| modelo / combinação | nota antes | nota depois | erros de sentido | lugar antes → depois |
|---|---|---|---|---|
| `deepgram/nova-3` + `openai/gpt-4.1-mini` (padrão antes) | 96,5 | 96,5 | 4 | 1º (padrão) → desclassificado |
| `thinkingmachines/inkling` (um passo) | 97,0 | 97,7 | 0 | empatado, mais lento → **padrão** |
| `deepgram/nova-3` (transcrição) | 96,2 | 96,1 | 4 | 1º → desclassificado |
| `openai/gpt-transcribe` (transcrição) | 95,6 | 96,5 | 1 | 2º → desclassificado (dipirona) |
| `openai/gpt-4o-mini-transcribe` (transcrição) | 94,7 | 95,8 | 0 | 8º → **1º sem erro, padrão ao vivo** |
| `google/chirp-3` (transcrição) | 95,3 | 96,3 | 0 | 3º → melhor nota sem erro, 7,6 s |
| `openai/gpt-transcribe` + `anthropic/claude-haiku-4.5` (padrão das duas passadas) | 95,4 | 96,2 | 1 | 15º → 2º das com 1 erro |

### Normalização (para erro de palavra e para erros de sentido)

| escrito de um jeito | = | do outro |
|---|---|---|
| número por extenso até 999, com "e" ("cento e vinte e oito", "sessenta e oito") | = | algarismo ("128", "68") |
| "dezoito mil", "18 mil", "18.000" | = | "18000" |
| "96 por cento", "96%" | = | "96 porcento" |
| "120/80", "120x80" | = | "120 por 80" |
| "14h30", "14:30" | = | "14 e 30" ("quatorze e trinta") |
| "7h", "13h00", "7:00" | = | "7 horas" |
| "800ml", "5mg" | = | "800 ml", "5 mg" |
| "Sra.", "Sr.", "Dra.", "Dr." | = | "senhora", "senhor", "doutora", "doutor" |
| "mg", "ml", "PA", "FC", "RX", "h" | = | "miligramas", "mililitros", "pressão arterial", "frequência cardíaca", "raio x", "horas" |

"um"/"uma" viram "1" dos dois lados (como no `normaliza` do teclado), mas não contam como número nos erros de
sentido: são quase sempre artigo.

### Erros que mudam sentido

Contados à parte, sem entrar na média: prefixo de negação trocado ("afebril"/"febril", "normal"/"anormal"),
negação perdida ou acrescida ("não", "nem", "sem", "nega", "nunca"…), número diferente, sumido ou acrescido
depois da normalização, e termo clínico (lista em `TERMOS_CLINICOS` da ferramenta) trocado por outra palavra ou
sumido ("azitromicina" → "trombicina"). Diferença só de acento não conta. Gabarito corrigido; nas
transcrições + formatação só a do `gpt-4.1` (as outras herdam os da transcrição, que a guarda não deixa a
formatação mudar):

| combinação | modelo | erros | ocorrências (item: tipo — gabarito → saída) |
|---|---|---|---|
| Transcrição sozinha | `google/chirp-3` | 0 | nenhum |
| Transcrição sozinha | `openai/gpt-4o-mini-transcribe` | 0 | nenhum |
| Transcrição sozinha | `google/gemini-3.5-transcribe` | 0 | nenhum |
| Transcrição sozinha | `openai/gpt-transcribe` | 1 | 12: termo clínico — «dipirona» → «de pirona» |
| Transcrição sozinha | `microsoft/mai-transcribe-2` | 1 | longo-6: negação — «» → «nunca aconteceu nada grave» |
| Transcrição sozinha | `qwen/qwen3-asr-flash-2026-02-10` | 2 | longo-3: termo clínico — «eupneico» → «eufneico»; longo-3: termo clínico — «mantenho ceftriaxona» → «mantém ceftraxona» |
| Transcrição sozinha | `openai/gpt-4o-transcribe` | 4 | 20: termo clínico — «cefaleia» → «se faleia»; longo-3: termo clínico — «eupneico em» → «e opnei com»; longo-3: termo clínico — «ceftriaxona» → «ceftraxona»; longo-4: termo clínico — «colecistectomia» → «colestectomia» |
| Transcrição sozinha | `deepgram/nova-3` | 4 | longo-3: negação no prefixo — «afebril há» → «febril a»; longo-3: termo clínico — «eupneico» → «eopneio»; longo-3: termo clínico — «estertores» → «estatutores»; longo-3: termo clínico — «azitromicina» → «as trombicina» |
| Transcrição sozinha | `mistralai/voxtral-small-24b-2507-stt` | 4 | 11: termo clínico — «segue com dispneia pedi» → «saikun tspineia pediu»; 11: termo clínico — «tórax» → «torques»; longo-3: termo clínico — «alta» → «o alto»; longo-4: termo clínico — «colecistectomia» → «colestectomia» |
| Transcrição sozinha | `assemblyai/universal-3-5-pro` | 5 | 13: termo clínico — «eupneica» → «eupineica»; longo-3: termo clínico — «eupneico» → «eupineico»; longo-3: termo clínico — «estertores» → «extertores»; longo-3: termo clínico — «alta hospitalar» → «auto-hospitalar»; continua-20s: termo clínico — «eupneica» → «eupineica» |
| Transcrição sozinha | `microsoft/mai-transcribe-1.5` | 7 | 13: termo clínico — «eupneica» → «eupenêica»; 20: termo clínico — «cefaleia» → «cefalea»; longo-3: termo clínico — «afebril» → «a febril»; longo-3: termo clínico — «eupneico» → «e o pneico»; longo-4: termo clínico — «colecistectomia» → «colestectomia»; longo-6: negação — «» → «nunca aconteceu nada grave»; continua-20s: termo clínico — «eupneica» → «eepinéica» |
| Transcrição sozinha | `mistralai/voxtral-mini-3b-2507` | 7 | 11: termo clínico — «segue com dispneia pedi» → «se é que conta espineia pediu»; 11: termo clínico — «tórax» → «torques»; 12: termo clínico — «dipirona» → «de pirôna»; 18: termo clínico — «benzodiazepínico» → «benzo jaze pínico eu»; longo-3: termo clínico — «afebril» → «a febril»; longo-3: termo clínico — «alta hospitalar» → «o auto-hospitalar»; continua-20s: termo clínico — «segue com dispneia pedi» → «siqueira contos pneia pediu» |
| Transcrição sozinha | `x-ai/grok-stt-1.0` | 8 | 12: termo clínico — «dipirona» → «de pirona»; 18: termo clínico — «benzodiazepínico» → «benzo diazepam»; 19: termo clínico — «transferência» → «transferências»; longo-3: termo clínico — «afebril há» → «a febril a»; longo-3: termo clínico — «estertores» → «estatores»; longo-3: termo clínico — «mantenho ceftriaxona» → «mantenha o ceftraxona»; longo-3: termo clínico — «avalio alta» → «avalie o auto»; longo-4: termo clínico — «colecistectomia» → «colestectomia» |
| Transcrição sozinha | `mistralai/voxtral-mini-transcribe` | 8 | 11: termo clínico — «dispneia pedi» → «dispineia perdi»; 18: termo clínico — «benzodiazepínico» → «benzo de azepínico eu»; longo-3: termo clínico — «afebril há» → «a febril a»; longo-3: termo clínico — «eupneico em» → «e opinei com»; longo-3: termo clínico — «estertores» → «estatores»; longo-3: termo clínico — «ceftriaxona» → «cefetraxona»; longo-3: termo clínico — «alta hospitalar» → «o auto-hospitalar»; continua-20s: termo clínico — «dispneia» → «dispineia» |
| Transcrição sozinha | `meta/muse-voice-transcribe-1.0` | 9 | 10: negação no prefixo — «me avisa» → «minha visa»; 13: termo clínico — «ela está eupneica» → «elista eu pineika»; 15: negação no prefixo — «prescrevi amoxicilina» → «prescrevia moxicilina»; 17: termo clínico — «a tomografia» → «automografia»; 18: termo clínico — «benzodiazepínico» → «benzo de diazepínico eu»; 19: termo clínico — «solicitei transferência» → «solicite transfers»; 20: termo clínico — «cefaleia» → «se faleia»; longo-3: termo clínico — «afebril há» → «a febril a»; longo-3: termo clínico — «mantenho ceftriaxona» → «mantém-se aftraxona» |
| Transcrição sozinha | `openai/whisper-large-v3-turbo` | 9 | 12: termo clínico — «a dipirona» → «de pirona»; 18: termo clínico — «benzodiazepínico» → «benzo de azepínico eu»; longo-3: termo clínico — «afebril há» → «a febril a»; longo-3: termo clínico — «eupneico em ar» → «eu opinei que era»; longo-3: termo clínico — «estertores» → «extastores»; longo-3: termo clínico — «mantenho ceftriaxona» → «mantém o ceftraxona»; longo-3: termo clínico — «avalio alta hospitalar» → «avalie o auto-hospitalar»; longo-4: termo clínico — «colecistectomia» → «colestectomia»; longo-4: termo clínico — «deambulando» → «bem bom né» |
| Transcrição sozinha | `qwen/qwen3-asr-1.7b` | 13 | 11: termo clínico — «dispneia pedi» → «espinheia pediu»; 11: termo clínico — «tórax» → «toraks»; 13: termo clínico — «eupneica» → «eupeica»; 15: termo clínico — «clavulanato» → «clavulonato»; 18: termo clínico — «benzodiazepínico» → «benzo diazepínico»; 20: termo clínico — «cefaleia» → «se falei»; longo-3: termo clínico — «afebril há» → «febreu a»; longo-3: termo clínico — «eupneico em» → «eu opinei com»; longo-3: termo clínico — «murmúrio» → «murmure»; longo-3: termo clínico — «mantenho ceftriaxona» → «mantém-se eftraxona»; continua-20s: termo clínico — «dispneia pedi» → «espinheia pediu»; continua-20s: termo clínico — «tórax» → «torác»; continua-20s: termo clínico — «eupneica» → «eupeíca» |
| Transcrição sozinha | `fish-audio/transcribe-1` | 13 | 11: termo clínico — «dispneia pedi» → «espinheia pediu»; 11: termo clínico — «tórax» → «toraks»; 13: termo clínico — «eupneica» → «eupeica»; 15: termo clínico — «clavulanato» → «clavulonato»; 18: termo clínico — «benzodiazepínico» → «benzo diazepínico»; 20: termo clínico — «cefaleia» → «se falei»; longo-3: termo clínico — «afebril há» → «febre baixa a»; longo-3: termo clínico — «eupneico em» → «eu opinei com»; longo-3: termo clínico — «minuto hemograma» → «minuto.hemograma»; longo-3: termo clínico — «ceftriaxona» → «ceftracsona»; longo-4: termo clínico — «alterações leito» → «alterações.leito»; continua-20s: termo clínico — «segue com dispneia pedi» → «que conta espinheia pediu»; continua-20s: termo clínico — «eupneica» → «eupeíca» |
| Transcrição sozinha | `openai/whisper-1` | 13 | 11: termo clínico — «segue com dispneia pedi» → «saiu de quicondospineia e perdeu»; 12: termo clínico — «dipirona» → «de pirônia»; 13: termo clínico — «eupneica» → «eupineica»; 18: termo clínico — «benzodiazepínico» → «benzo de azepinco»; longo-3: termo clínico — «afebril há» → «a febril a»; longo-3: termo clínico — «eupneico» → «eupineico»; longo-3: termo clínico — «estertores» → «extestores»; longo-3: termo clínico — «ceftriaxona» → «ceftraxona»; longo-3: termo clínico — «alta» → «auto»; longo-4: termo clínico — «colecistectomia» → «colestectomia»; longo-4: termo clínico — «deambulando» → «deambulante»; continua-20s: termo clínico — «dispneia pedi» → «dospinéia perdi»; continua-20s: termo clínico — «eupneica» → «eupineica» |
| Transcrição sozinha | `openai/whisper-large-v3` | 15 | 11: termo clínico — «segue com dispneia pedi» → «isso aí que conta espinéia perdi»; 11: termo clínico — «tórax» → «torques»; 13: termo clínico — «eupneica» → «eu-pineica»; 18: termo clínico — «benzodiazepínico» → «benzo de azepeco eu»; longo-2: número — «7» → «19 horas»; longo-3: termo clínico — «afebril há» → «a febril a»; longo-3: termo clínico — «eupneico em» → «eu opinei que o»; longo-3: termo clínico — «estertores» → «estetores»; longo-3: termo clínico — «ceftriaxona» → «ceftraxona»; longo-3: termo clínico — «de tórax» → «auditórica»; longo-3: termo clínico — «avalio alta» → «avalie o auto»; longo-4: termo clínico — «colecistectomia» → «colestectomia»; longo-4: termo clínico — «deambulando» → «bem abundante»; continua-20s: termo clínico — «dispneia pedi» → «dispineia pediu»; continua-20s: termo clínico — «eupneica» → «eupineica» |
| Transcrição sozinha | `nvidia/parakeet-tdt-0.6b-v3` | 19 | 11: termo clínico — «dispneia» → «espineia»; 11: termo clínico — «tórax» → «tóxicos»; 12: termo clínico — «dipirona» → «de pirona»; 15: termo clínico — «clavulanato» → «clavolanato»; 17: termo clínico — «broncopneumonia» → «bronco pneumonia»; 18: termo clínico — «benzodiazepínico» → «benzo de azepinco»; longo-3: termo clínico — «afebril há» → «a febril a»; longo-3: termo clínico — «eupneico» → «e opneico»; longo-3: termo clínico — «crepitantes» → «creptantes»; longo-3: termo clínico — «ceftriaxona» → «ceftraxona»; longo-3: termo clínico — «azitromicina» → «acitomicina»; longo-3: termo clínico — «tórax» → «»; longo-3: termo clínico — «alta hospitalar» → «o auto-hospitalar»; longo-4: termo clínico — «colecistectomia» → «colestistectomia»; longo-4: termo clínico — «captopril» → «captoprivo»; longo-4: termo clínico — «alta» → «auto»; continua-20s: termo clínico — «dispneia pedi» → «dispineia pede»; continua-20s: termo clínico — «eupneica» → «eupineica»; continua-20s: termo clínico — «broncopneumonia» → «bronco pneumonia» |
| Transcrição sozinha | `qwen/qwen3-asr-0.6b` | 23 | 11: termo clínico — «segue com dispneia pedi» → «saiu que contou spineia pediu»; 11: termo clínico — «tórax» → «torx»; 12: termo clínico — «dipirona» → «de pirana»; 15: termo clínico — «amoxicilina» → «amoxifenilina»; 18: termo clínico — «benzodiazepínico» → «benzinho de azepinho»; 19: termo clínico — «a uti» → «o ti»; 20: termo clínico — «cefaleia» → «se falei»; longo-3: termo clínico — «afebril há» → «a febre o a»; longo-3: termo clínico — «eupneico em» → «eu penei com»; longo-3: termo clínico — «vesicular» → «vesicoar»; longo-3: termo clínico — «estertores crepitantes» → «estatutos crespantes»; longo-3: termo clínico — «ceftriaxona» → «f track sona»; longo-3: termo clínico — «azitromicina» → «astromicina»; longo-3: termo clínico — «de tórax» → «auditória»; longo-3: termo clínico — «alta» → «ao»; longo-4: termo clínico — «pós-operatório» → «pós operatório»; longo-4: termo clínico — «colecistectomia» → «colostectomia»; longo-4: termo clínico — «hipertensiva» → «persistiva»; continua-20s: termo clínico — «segue com dispneia pedi» → «saiu de conto spinnea pediu»; continua-20s: termo clínico — «tórax» → «torx»; continua-20s: termo clínico — «glicemia» → «gliserina»; continua-20s: termo clínico — «broncopneumonia» → «bronco-pneumonia»; continua-20s: termo clínico — «a uti» → «o tei» |
| Transcrição sozinha | `fish-audio/transcribe-1-pro` | 29 | 01: número — «» → «speaker 0»; 02: número — «» → «speaker 0 batida de língua»; 03: número — «» → «speaker 0»; 04: número — «» → «speaker 0»; 05: número — «» → «speaker 0»; 06: número — «» → «speaker 0»; 07: número — «» → «speaker 0»; 08: número — «» → «speaker 0»; 09: número — «» → «speaker 0»; 10: número — «» → «speaker 0»; 11: número — «» → «speaker 0»; 11: termo clínico — «segue com dispneia pedi» → «isso aí que cunha spinale pediu»; 12: número — «» → «speaker 0»; 13: número — «» → «speaker 0»; 14: número — «» → «speaker 0»; 15: número — «» → «speaker 0»; 16: número — «» → «speaker 0»; 17: número — «» → «speaker 0»; 18: número — «» → «speaker 0»; 19: número — «» → «speaker 0»; 20: número — «» → «speaker 0»; longo-1: número — «» → «speaker 0 batida»; longo-2: número — «» → «speaker 0»; longo-3: número — «» → «speaker 0»; longo-4: número — «» → «speaker 0»; longo-5: número — «» → «speaker 0»; longo-6: número — «» → «speaker 0»; continua-20s: número — «» → «speaker 0»; continua-20s: termo clínico — «eupneica» → «eupinênica» |
| Transcrição sozinha | `nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b` | 31 | 11: termo clínico — «dispneia pedi» → «os pineia pediu»; 11: termo clínico — «tórax» → «tóxica»; 12: termo clínico — «dipirona» → «dipira»; 13: termo clínico — «eupneica» → «eupineica»; 15: termo clínico — «amoxicilina» → «a moxiccilina»; 15: termo clínico — «clavulanato» → «clavolanato»; 18: termo clínico — «benzodiazepínico» → «benzo de azeping»; 19: termo clínico — «a uti» → «o ti»; longo-2: número — «14 e 30 na» → «»; longo-3: termo clínico — «afebril» → «a febril»; longo-3: termo clínico — «eupneico» → «eu opinei com»; longo-3: termo clínico — «estertores» → «exterstores»; longo-3: número — «80» → «»; longo-3: termo clínico — «leucócitos» → «leucostos»; longo-3: termo clínico — «ceftriaxona» → «se a sona»; longo-3: termo clínico — «azitromicina» → «astromicina»; longo-3: termo clínico — «tórax» → «tóricas»; longo-3: termo clínico — «avalio alta» → «avaliou o alto»; longo-4: termo clínico — «pós-operatório» → «pós operatório»; longo-4: termo clínico — «colecistectomia» → «colestecectomia»; longo-4: termo clínico — «deambulando» → «deambulante»; longo-4: número — «2» → «ii»; longo-4: termo clínico — «furosemida» → «furozemida»; longo-4: número — «800» → «»; longo-4: termo clínico — «hipertensiva» → «pertensiva»; longo-4: termo clínico — «captopril» → «captor privo»; longo-4: número — «6» → «»; continua-20s: termo clínico — «dispneia pedi» → «spineia pediu»; continua-20s: termo clínico — «eupneica» → «eu pneica»; continua-20s: termo clínico — «broncopneumonia» → «bronco pneumonia»; continua-20s: termo clínico — «a uti» → «o ti» |
| Um passo | `thinkingmachines/inkling` | 0 | nenhum |
| Um passo | `google/gemini-3.5-flash` | 0 | nenhum |
| Um passo | `google/gemini-3-flash-preview` | 0 | nenhum |
| Um passo | `google/gemini-3.1-flash-lite` | 0 | nenhum |
| Um passo | `google/gemini-3.8-flash` | 0 | nenhum |
| Um passo | `google/gemini-3.6-flash` | 0 | nenhum |
| Um passo | `google/gemini-3.7-flash` | 0 | nenhum |
| Um passo | `qwen/qwen3.8-omni-flash` | 0 | nenhum |
| Um passo | `openai/gpt-audio` | 1 | 20: termo clínico — «cefaleia» → «se falei» |
| Um passo | `openai/gpt-audio-mini` | 1 | 20: termo clínico — «cefaleia» → «se falei» |
| Um passo | `google/gemini-3.5-flash-lite` | 3 | 18: termo clínico — «benzodiazepínico» → «benzoilepico»; 20: termo clínico — «cefaleia» → «se fala aí»; longo-3: termo clínico — «mantenho ceftriaxona» → «mantém o ceftraxone» |
| Um passo | `google/gemini-2.5-flash` | 4 | 16: número — «2» → «12»; 20: termo clínico — «cefaleia» → «se a fala ele»; longo-3: termo clínico — «mantenho ceftriaxona» → «mantém o ceftraxona»; longo-3: termo clínico — «alta» → «o alto» |
| Um passo | `google/gemini-2.5-flash-lite` | 7 | 13: negação — «eupneica sem sinais de» → «em pne ensina a identificar»; 18: termo clínico — «benzodiazepínico» → «benzetacil»; 20: termo clínico — «cefaleia» → «se falei»; longo-4: termo clínico — «passagem de plantão da enfermaria» → «»; longo-4: termo clínico — «pós-operatório» → «pós operatório»; continua-20s: termo clínico — «segue com dispneia pedi» → «eu sei que tem pineia pediu»; continua-20s: termo clínico — «eupneica» → «eupineica» |
| Um passo | `xiaomi/mimo-v2.6-pro` | 12 | 11: termo clínico — «dispneia pedi» → «punção lombar e pediu»; 12: termo clínico — «a dipirona» → «de piranopar»; 14: termo clínico — «hemograma» → «eletrocardiograma»; 14: termo clínico — «a glicemia» → «agrissei-me»; 18: termo clínico — «sonolenta» → «sonolento»; 19: termo clínico — «a uti» → «o ti»; 20: termo clínico — «cefaleia» → «você falei»; longo-3: termo clínico — «internado» → «internada»; longo-3: termo clínico — «eupneico» → «eupneica»; longo-3: termo clínico — «antibiótico» → «antibioticoterapia»; continua-20s: termo clínico — «eupneica» → «eupneico»; continua-20s: termo clínico — «a uti» → «o ti» |
| Um passo | `xiaomi/mimo-v2.6-pro-ultraspeed` | 12 | 11: termo clínico — «dispneia pedi» → «punção lombar e pediu»; 12: termo clínico — «a dipirona» → «de piranopar»; 14: termo clínico — «hemograma» → «eletrocardiograma»; 14: termo clínico — «a glicemia» → «agressi-me»; 19: termo clínico — «a uti» → «o ti»; longo-3: termo clínico — «internado» → «internada»; longo-3: termo clínico — «eupneico» → «eupneica»; longo-3: termo clínico — «antibiótico» → «antibioticoterapia»; longo-4: termo clínico — «deambulando» → «em bom andamento»; longo-4: termo clínico — «senhor antônio insuficiência cardíaca» → «senhora anta em suspeita de cardiopatia»; continua-20s: termo clínico — «eupneica» → «eupneico»; continua-20s: termo clínico — «a uti» → «o ti» |
| Um passo | `xiaomi/mimo-v2.6-flash` | 13 | 13: termo clínico — «ela está eupneica» → «ele saiu pálido»; 14: termo clínico — «hemograma veio» → «meu programa de vida é»; 14: termo clínico — «a glicemia 1 pouco alterada» → «o abastecimento que foi alterado»; 17: termo clínico — «broncopneumonia» → «bronquiolite»; 18: termo clínico — «benzodiazepínico» → «benzetacil porque»; 18: termo clínico — «sonolenta» → «funcionando entre»; 20: termo clínico — «cefaleia» → «se falei»; 20: termo clínico — «analgesia» → «narcolgia»; longo-1: negação — «chegou lá na oficina» → «o alarme não aciona»; longo-2: número — «7 às 13 horas» → «cirurgias»; longo-2: número — «7» → «6»; longo-3: termo clínico — «tórax» → «tóriz»; longo-4: termo clínico — «deambulando» → «npo» |
| Um passo | `perceptron/perceptron-mk1.5` | 22 | 07: número — «sexta» → «às 6»; 11: termo clínico — «segue com dispneia pedi» → «c que conta espinheira perdeu»; 13: termo clínico — «eupneica» → «eopineica»; 14: termo clínico — «hemograma» → «emolograma»; 18: termo clínico — «benzodiazepínico» → «bem do jazepinho ele»; 18: termo clínico — «sonolenta» → «sonolento»; 19: termo clínico — «a uti» → «o ti»; 20: termo clínico — «cefaleia» → «se falei é»; 20: termo clínico — «analgesia» → «náusea»; longo-3: termo clínico — «afebril há» → «às fevereiro às»; longo-3: termo clínico — «eupneico em ar» → «eu opinei com o»; longo-3: termo clínico — «crepitantes» → «escapitantes»; longo-3: termo clínico — «alta» → «alto»; longo-3: negação no prefixo — «amanhã» → «manhã»; longo-4: termo clínico — «enfermaria» → «enfermeira»; longo-4: termo clínico — «pós-operatório» → «pós operatória»; longo-4: termo clínico — «deambulando» → «de ambulante»; longo-4: termo clínico — «insuficiência cardíaca» → «insusceitível cirúrgica»; longo-4: termo clínico — «crise hipertensiva» → «hipertensão»; continua-20s: termo clínico — «está eupneica» → «estava ortopnéica»; continua-20s: termo clínico — «transferência» → «transferências»; continua-20s: termo clínico — «a uti» → «o tei» |
| Transcrição + formatação | `openai/gpt-transcribe + openai/gpt-4.1` | 1 | 12: termo clínico — «dipirona» → «de pirona» |
| Transcrição + formatação | `deepgram/nova-3 + openai/gpt-4.1` | 4 | longo-3: negação no prefixo — «afebril há» → «febril a»; longo-3: termo clínico — «eupneico» → «eopneio»; longo-3: termo clínico — «estertores» → «estatutores»; longo-3: termo clínico — «azitromicina» → «as trombicina» |

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
que tira ~1,5–1,9 ponto de todos; a escolha é a mesma com os dois. A escolha usa o corrigido, que é o que foi dito.

## Métricas

- **Erro com acento** (ortografia, novo): distância de edição de palavras contra o gabarito, sobre as palavras
  do gabarito, com acento, cedilha e hífen **como escritos** ("esta" ≠ "está", "a" ≠ "à", "voce" ≠ "você",
  "segunda-feira" ≠ "segunda feira"), minúsculas **exceto nome próprio e sigla** do gabarito (palavra com
  maiúscula fora do começo de frase, ou toda em maiúsculas: "Mariana", "Antônio", "UTI", o "X" de "raio X"),
  que valem com a caixa escrita ("uti" ≠ "UTI"). Número por extenso = algarismo e abreviação = forma longa
  (tabela "Normalização"): é convenção de escrita, não ortografia.
- **Só acento**: quantas das trocas do caminho mínimo diferem **só** em acento ou cedilha ("esta"→"está").
  As trocas só de caixa de nome próprio também são contadas (`caixa` no JSON); deram zero em todos os modelos
  da nuvem.
- **Erro normalizado**: o mesmo, sem a caixa de nome próprio (tudo minúsculo). Começou como o
  `comparar.normaliza` do teclado, que já distinguia acento e hífen mas contava "cento e 20" × "120" e
  "Sra." × "senhora" como erro; na revisão os dois erros passaram a usar a normalização da tabela acima. Nenhum
  modelo da nuvem errou caixa de nome próprio, e só acento houve 0–2 trocas por modelo, por isso as duas
  colunas quase sempre coincidem.
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

Nas tabelas, "muda sentido" é o número de erros que mudam sentido com o gabarito corrigido (entre
parênteses, com o original); a ordem é a da regra: menos erros de sentido, depois a nota.

Todos os 24 modelos de `GET /api/v1/models?output_modalities=transcription` (28/set) aceitaram `language=pt`
e transcreveram os 27 itens sem erro.

| # | combinação | muda sentido | nota | erro com acento | erro normalizado | erro clínico | só acento | vírgula F1 | fim F1 | ? F1 | : F1 | maiúsc. | 20 s p50 / p95 | longos p50 / p95 | US$/min | nota (gab. original) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `google/chirp-3` | 0 (5) | 96,26 | 2,2 % | 2,2 % | 3,3 % | 0 | 0,88 | 0,96 | 1,00 | 0,00 | 0,99 | 7,6 s / 7,7 s | 12,4 s / 14,6 s | 0,0166 | 94,57 |
| 2 | `openai/gpt-4o-mini-transcribe` | 0 (5) | 95,79 | 0,9 % | 0,9 % | 0,7 % | 0 | 0,85 | 0,92 | 1,00 | 0,00 | 0,99 | 1,6 s / 2,9 s | 2,8 s / 3,4 s | 0,0018 | 94,33 |
| 3 | `google/gemini-3.5-transcribe` | 0 (5) | 94,77 | 1,3 % | 1,3 % | 1,9 % | 1 | 0,83 | 0,90 | 1,00 | 0,00 | 0,99 | 3,4 s / 3,7 s | 3,7 s / 4,0 s | 0,0030 | 93,42 |
| 4 | `openai/gpt-transcribe` | 1 (6) | 96,50 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,91 | 0,93 | 1,00 | 0,00 | 0,99 | 2,0 s / 2,8 s | 3,0 s / 4,4 s | 0,0047 | 94,92 |
| 5 | `microsoft/mai-transcribe-2` | 1 (6) | 95,93 | 1,3 % | 1,3 % | 0,7 % | 0 | 0,87 | 0,92 | 1,00 | 0,00 | 0,99 | 2,0 s / 2,3 s | 1,5 s / 2,2 s | 0,0017 | 94,04 |
| 6 | `qwen/qwen3-asr-flash-2026-02-10` | 2 (7) | 95,79 | 1,1 % | 1,1 % | 2,2 % | 0 | 0,88 | 0,90 | 1,00 | 0,50 | 0,98 | 2,3 s / 2,9 s | 4,5 s / 6,1 s | 0,0020 | 94,19 |
| 7 | `openai/gpt-4o-transcribe` | 4 (9) | 96,39 | 1,8 % | 1,8 % | 3,0 % | 0 | 0,91 | 0,93 | 1,00 | 0,00 | 0,99 | 2,1 s / 2,6 s | 3,6 s / 4,9 s | 0,0035 | 94,81 |
| 8 | `deepgram/nova-3` | 4 (9) | 96,13 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,88 | 0,97 | 1,00 | 0,00 | 1,00 | 1,9 s / 2,0 s | 1,7 s / 2,3 s | 0,0043 | 94,25 |
| 9 | `mistralai/voxtral-small-24b-2507-stt` | 4 (9) | 94,99 | 2,0 % | 2,0 % | 4,1 % | 0 | 0,87 | 0,89 | 1,00 | 0,00 | 0,99 | 5,1 s / 6,2 s | 9,3 s / 9,6 s | 0,0030 | 93,08 |
| 10 | `assemblyai/universal-3-5-pro` | 5 (10) | 92,24 | 2,0 % | 2,0 % | 3,3 % | 0 | 0,77 | 0,91 | 0,92 | 0,67 | 0,98 | 2,7 s / 3,3 s | 2,3 s / 3,6 s | 0,0037 | 90,55 |
| 11 | `microsoft/mai-transcribe-1.5` | 7 (12) | 95,16 | 2,4 % | 2,4 % | 3,7 % | 0 | 0,87 | 0,91 | 1,00 | 0,00 | 0,99 | 2,1 s / 3,4 s | 2,1 s / 2,7 s | 0,0062 | 93,25 |
| 12 | `mistralai/voxtral-mini-3b-2507` | 7 (12) | 91,97 | 4,1 % | 4,1 % | 7,4 % | 0 | 0,77 | 0,87 | 1,00 | 0,00 | 0,98 | 2,3 s / 2,8 s | 2,6 s / 4,5 s | 0,0010 | 90,20 |
| 13 | `x-ai/grok-stt-1.0` | 8 (12) | 95,85 | 4,1 % | 4,1 % | 7,4 % | 0 | 0,92 | 0,95 | 1,00 | 0,00 | 0,99 | 1,9 s / 2,7 s | 2,4 s / 3,5 s | 0,0017 | 94,02 |
| 14 | `mistralai/voxtral-mini-transcribe` | 8 (13) | 93,00 | 3,7 % | 3,7 % | 6,3 % | 0 | 0,80 | 0,89 | 1,00 | 0,00 | 0,98 | 1,9 s / 59,3 s | 2,7 s / 3,4 s | 0,0029 | 91,41 |
| 15 | `meta/muse-voice-transcribe-1.0` | 9 (13) | 88,75 | 5,1 % | 5,1 % | 8,9 % | 0 | 0,64 | 0,83 | 1,00 | 0,00 | 0,97 | 7,3 s / 9,0 s | 10,9 s / 15,0 s | 0,0030 | 87,29 |
| 16 | `openai/whisper-large-v3-turbo` | 9 (14) | 83,55 | 6,6 % | 6,6 % | 11,5 % | 2 | 0,59 | 0,76 | 0,86 | 0,00 | 0,95 | 3,6 s / 10,4 s | 2,9 s / 3,5 s | 0,0002 | 81,89 |
| 17 | `qwen/qwen3-asr-1.7b` | 13 (17) | 92,68 | 4,5 % | 4,5 % | 8,5 % | 2 | 0,83 | 0,87 | 1,00 | 0,00 | 0,97 | 2,6 s / 4,8 s | 2,2 s / 3,0 s | 0,0005 | 91,45 |
| 18 | `fish-audio/transcribe-1` | 13 (17) | 91,81 | 6,2 % | 6,2 % | 9,6 % | 3 | 0,81 | 0,89 | 1,00 | 0,00 | 0,98 | 1,7 s / 3,5 s | 2,6 s / 2,9 s | 0,0062 | 90,58 |
| 19 | `openai/whisper-1` | 13 (18) | 88,69 | 5,2 % | 5,2 % | 8,5 % | 2 | 0,73 | 0,74 | 1,00 | 0,00 | 0,97 | 3,0 s / 3,5 s | 3,8 s / 5,8 s | 0,0062 | 86,66 |
| 20 | `openai/whisper-large-v3` | 15 (20) | 82,40 | 7,3 % | 7,3 % | 13,0 % | 0 | 0,60 | 0,73 | 0,83 | 0,00 | 0,96 | 7,1 s / 9,4 s | 5,2 s / 29,7 s | 0,0009 | 80,72 |
| 21 | `nvidia/parakeet-tdt-0.6b-v3` | 19 (23) | 87,89 | 7,4 % | 7,4 % | 11,9 % | 1 | 0,67 | 0,82 | 1,00 | 0,00 | 0,97 | 1,5 s / 2,3 s | 1,7 s / 2,3 s | 0,0015 | 86,20 |
| 22 | `qwen/qwen3-asr-0.6b` | 23 (28) | 82,90 | 10,5 % | 10,3 % | 15,6 % | 1 | 0,68 | 0,78 | 0,83 | 1,00 | 0,96 | 1,8 s / 2,4 s | 1,9 s / 3,4 s | 0,0002 | 81,16 |
| 23 | `fish-audio/transcribe-1-pro` | 29 (34) | 90,09 | 10,8 % | 10,8 % | 13,7 % | 2 | 0,87 | 0,93 | 0,92 | 0,00 | 0,99 | 1,6 s / 2,3 s | 2,8 s / 3,3 s | 0,0062 | 88,46 |
| 24 | `nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b` | 31 (35) | 44,90 | 10,2 % | 10,2 % | 18,1 % | 2 | 0,00 | 0,00 | 0,00 | 0,00 | 0,92 | 2,0 s / 3,3 s | 3,1 s / 3,7 s | 0,0002 | 43,49 |

Rascunho do Nemotron (motor do aparelho, refeito no Mac como em `docs/medicao-duas-passadas.md`), para
referência:

| # | combinação | muda sentido | nota | erro com acento | erro normalizado | erro clínico | só acento | vírgula F1 | fim F1 | ? F1 | : F1 | maiúsc. | 20 s p50 / p95 | longos p50 / p95 | US$/min | nota (gab. original) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `Nemotron no Mac (rascunho)` | 26 (28) | 46,04 | 10,1 % | 10,1 % | 16,7 % | 1 | 0,06 | 0,00 | 0,00 | 0,00 | 0,93 | — / — | — / — | 0 | 44,65 |

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

| # | combinação | muda sentido | nota | erro com acento | erro normalizado | erro clínico | só acento | vírgula F1 | fim F1 | ? F1 | : F1 | maiúsc. | 20 s p50 / p95 | longos p50 / p95 | US$/min | nota (gab. original) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `thinkingmachines/inkling` | 0 (5) | 97,71 | 1,4 % | 1,4 % | 1,5 % | 0 | 0,94 | 0,96 | 1,00 | 1,00 | 0,99 | 4,2 s / 5,1 s | 5,5 s / 7,8 s | 0,0049 | 96,10 |
| 2 | `google/gemini-3.5-flash` | 0 (5) | 95,69 | 0,8 % | 0,8 % | 0,7 % | 0 | 0,86 | 0,91 | 1,00 | 0,00 | 0,98 | 3,1 s / 6,2 s | 3,9 s / 6,0 s | 0,0073 | 94,23 |
| 3 | `google/gemini-3-flash-preview` | 0 (5) | 95,40 | 0,9 % | 0,9 % | 0,7 % | 0 | 0,84 | 0,91 | 1,00 | 0,00 | 0,99 | 3,3 s / 3,7 s | 3,7 s / 4,3 s | 0,0024 | 93,50 |
| 4 | `google/gemini-3.1-flash-lite` | 0 (5) | 95,36 | 0,8 % | 0,8 % | 0,4 % | 0 | 0,84 | 0,91 | 1,00 | 0,00 | 0,99 | 2,6 s / 3,6 s | 3,3 s / 4,5 s | 0,0012 | 93,76 |
| 5 | `google/gemini-3.8-flash` | 0 (5) | 94,48 | 1,3 % | 1,3 % | 0,7 % | 2 | 0,85 | 0,92 | 0,93 | 0,00 | 0,99 | 4,0 s / 4,6 s | 4,9 s / 6,1 s | 0,0023 | 92,58 |
| 6 | `google/gemini-3.6-flash` | 0 (5) | 94,00 | 1,3 % | 1,3 % | 1,1 % | 1 | 0,84 | 0,90 | 0,93 | 0,67 | 0,99 | 3,5 s / 4,6 s | 4,0 s / 5,2 s | 0,0024 | 92,09 |
| 7 | `google/gemini-3.7-flash` | 0 (5) | 93,91 | 1,8 % | 1,8 % | 2,2 % | 0 | 0,86 | 0,90 | 0,93 | 0,25 | 0,99 | 3,8 s / 4,1 s | 4,0 s / 5,4 s | 0,0024 | 92,27 |
| 8 | `qwen/qwen3.8-omni-flash` | 0 (5) | 92,89 | 0,6 % | 0,6 % | 0,4 % | 0 | 0,82 | 0,94 | 0,83 | 1,00 | 0,99 | 7,3 s / 37,0 s | 8,2 s / 10,5 s | 0,0003 | 91,24 |
| 9 | `openai/gpt-audio` | 1 (6) | 96,61 | 1,4 % | 1,4 % | 1,9 % | 0 | 0,91 | 0,93 | 1,00 | 0,67 | 0,99 | 2,6 s / 3,7 s | 3,7 s / 4,5 s | 0,0228 | 94,84 |
| 10 | `openai/gpt-audio-mini` (trava 1/27) | 1 (6) | 94,90 | 2,6 % | 2,6 % | 3,0 % | 0 | 0,87 | 0,90 | 1,00 | 0,50 | 0,99 | 2,3 s / 3,6 s | 3,1 s / 4,0 s | 0,0012 | 93,25 |
| 11 | `google/gemini-3.5-flash-lite` | 3 (8) | 95,17 | 2,3 % | 2,3 % | 4,4 % | 0 | 0,90 | 0,94 | 0,93 | 1,00 | 0,99 | 3,0 s / 3,1 s | 3,1 s / 4,2 s | 0,0011 | 93,42 |
| 12 | `google/gemini-2.5-flash` | 4 (9) | 92,55 | 3,3 % | 3,3 % | 6,7 % | 0 | 0,83 | 0,90 | 0,92 | 0,00 | 0,98 | 3,7 s / 4,5 s | 3,5 s / 4,9 s | 0,0023 | 90,76 |
| 13 | `google/gemini-2.5-flash-lite` | 7 (12) | 92,92 | 4,7 % | 4,7 % | 8,9 % | 0 | 0,87 | 0,93 | 0,92 | 0,00 | 0,99 | 3,3 s / 3,4 s | 3,9 s / 4,3 s | 0,0006 | 91,19 |
| 14 | `xiaomi/mimo-v2.6-pro` | 12 (17) | 88,70 | 10,7 % | 10,7 % | 10,7 % | 0 | 0,85 | 0,89 | 0,91 | 1,00 | 0,99 | 10,4 s / 39,3 s | 9,6 s / 24,8 s | 0,0005 | 87,24 |
| 15 | `xiaomi/mimo-v2.6-pro-ultraspeed` | 12 (17) | 88,55 | 11,9 % | 11,9 % | 12,6 % | 0 | 0,86 | 0,90 | 0,91 | 1,00 | 0,99 | 3,6 s / 7,1 s | 4,7 s / 5,7 s | 0,0049 | 87,09 |
| 16 | `xiaomi/mimo-v2.6-flash` (trava 1/27) | 13 (17) | 87,89 | 13,5 % | 13,5 % | 14,4 % | 0 | 0,83 | 0,93 | 0,92 | 0,00 | 0,99 | 6,7 s / 16,4 s | 8,1 s / 18,0 s | 0,0002 | 86,62 |
| 17 | `perceptron/perceptron-mk1.5` (trava 1/27) | 22 (25) | 88,63 | 8,8 % | 8,8 % | 15,9 % | 1 | 0,77 | 0,89 | 0,92 | 0,00 | 0,99 | 5,7 s / 6,2 s | 9,5 s / 10,8 s | 0,0006 | 86,89 |
| 18 | `google/gemini-2.5-pro` | 0 (0) | — | 0,0 % | 0,0 % | — | 0 | 0,00 | 0,50 | 1,00 | — | 0,89 | — / — | — / — | 0,1532 | — |
| 19 | `google/gemini-3.1-pro-preview` | 0 (0) | — | 0,0 % | 0,0 % | — | 0 | 1,00 | 1,00 | 1,00 | — | 1,00 | — / — | — / — | 0,0925 | — |
| 20 | `xiaomi/mimo-v2.5` | 0 (0) | — | 5,6 % | 5,6 % | — | 0 | 0,67 | — | — | — | 0,94 | — / — | — / — | 0,0005 | — |
| 21 | `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free` (trava 9/9) | 1 (1) | — | 100,0 % | 100,0 % | — | 0 | — | — | — | — | — | — / — | — / — | 0,0000 | — |
| 22 | `mistralai/voxtral-small-24b-2507` | 2 (4) | — | 3,1 % | 3,1 % | 6,1 % | 0 | 0,62 | 0,55 | 1,00 | — | 0,96 | — / — | — / — | 0,0054 | — |

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

| # | combinação | muda sentido | nota | erro com acento | erro normalizado | erro clínico | só acento | vírgula F1 | fim F1 | ? F1 | : F1 | maiúsc. | 20 s p50 / p95 | longos p50 / p95 | US$/min | nota (gab. original) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `openai/gpt-transcribe + google/gemini-3.5-flash-lite` (recusas 1/27) | 1 (6) | 96,50 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,91 | 0,93 | 1,00 | 0,00 | 0,99 | 4,2 s / 4,4 s | 5,0 s / 6,5 s | 0,0055 | 94,92 |
| 2 | `openai/gpt-transcribe + anthropic/claude-haiku-4.5` (recusas 1/27) | 1 (6) | 96,24 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,6 s / 4,9 s | 4,6 s / 6,3 s | 0,0073 | 94,66 |
| 3 | `openai/gpt-transcribe + anthropic/claude-sonnet-5` (recusas 1/27) | 1 (6) | 96,24 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 5,1 s / 5,7 s | 7,1 s / 9,2 s | 0,0113 | 94,66 |
| 4 | `openai/gpt-transcribe + deepseek/deepseek-v4.1-flash` (recusas 0/27) | 1 (6) | 96,24 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,6 s / 6,1 s | 5,8 s / 12,3 s | 0,0050 | 94,66 |
| 5 | `openai/gpt-transcribe + google/gemini-3.1-flash-lite` (recusas 1/27) | 1 (6) | 96,24 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,0 s / 6,4 s | 5,1 s / 6,8 s | 0,0052 | 94,66 |
| 6 | `openai/gpt-transcribe + google/gemini-3.8-flash` (recusas 1/27) | 1 (6) | 96,24 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,2 s / 6,1 s | 5,9 s / 7,4 s | 0,0064 | 94,66 |
| 7 | `openai/gpt-transcribe + qwen/qwen3.8-flash` (recusas 0/27) | 1 (6) | 96,24 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,89 | 0,93 | 1,00 | 0,00 | 0,99 | 4,7 s / 6,0 s | 6,5 s / 10,2 s | 0,0049 | 94,66 |
| 8 | `openai/gpt-transcribe + openai/gpt-4.1` (recusas 1/27) | 1 (6) | 96,06 | 1,5 % | 1,5 % | 2,6 % | 0 | 0,88 | 0,93 | 1,00 | 0,00 | 0,99 | 3,8 s / 4,5 s | 5,0 s / 6,8 s | 0,0084 | 94,47 |
| 9 | `openai/gpt-transcribe + openai/gpt-4.1-mini` (recusas 2/27) | 1 (6) | 96,03 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,88 | 0,93 | 1,00 | 0,00 | 0,99 | 5,2 s / 12,2 s | 5,2 s / 7,1 s | 0,0054 | 94,45 |
| 10 | `openai/gpt-transcribe + mistralai/mistral-small-3.2-24b-instruct` (recusas 2/27) | 1 (6) | 95,97 | 1,5 % | 1,5 % | 2,6 % | 0 | 0,88 | 0,93 | 1,00 | 0,00 | 0,99 | 5,0 s / 7,3 s | 10,6 s / 15,9 s | 0,0048 | 94,38 |
| 11 | `openai/gpt-transcribe + meta-llama/llama-4-maverick` (recusas 2/27) | 1 (6) | 95,66 | 1,5 % | 1,5 % | 2,6 % | 0 | 0,86 | 0,93 | 1,00 | 0,00 | 0,99 | 9,5 s / 11,8 s | 22,5 s / 27,0 s | 0,0050 | 94,07 |
| 12 | `openai/gpt-transcribe + openai/gpt-4o-mini` (recusas 1/27) | 1 (6) | 95,58 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,86 | 0,92 | 1,00 | 0,00 | 0,99 | 4,6 s / 4,9 s | 6,7 s / 8,5 s | 0,0049 | 93,99 |
| 13 | `openai/gpt-transcribe + openai/gpt-6-luna` (recusas 0/27) | 1 (6) | 95,46 | 1,4 % | 1,4 % | 2,6 % | 0 | 0,85 | 0,92 | 1,00 | 0,00 | 0,99 | 4,2 s / 4,5 s | 5,4 s / 7,3 s | 0,0049 | 93,87 |
| 14 | `openai/gpt-transcribe + mistralai/mistral-medium-3.1` (recusas 4/27) | 2 (7) | 95,75 | 1,7 % | 1,7 % | 3,0 % | 0 | 0,87 | 0,93 | 1,00 | 0,00 | 0,99 | 4,0 s / 6,0 s | 4,7 s / 6,8 s | 0,0055 | 94,16 |
| 15 | `deepgram/nova-3 + anthropic/claude-sonnet-5` (recusas 1/27) | 4 (9) | 96,57 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,91 | 0,97 | 1,00 | 0,00 | 1,00 | 5,5 s / 6,8 s | 5,4 s / 19,0 s | 0,0130 | 94,70 |
| 16 | `deepgram/nova-3 + google/gemini-3.8-flash` (recusas 1/27) | 4 (9) | 96,55 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,91 | 0,97 | 1,00 | 0,00 | 1,00 | 4,8 s / 6,2 s | 4,1 s / 5,5 s | 0,0060 | 94,68 |
| 17 | `deepgram/nova-3 + google/gemini-3.1-flash-lite` (recusas 0/27) | 4 (9) | 96,47 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,6 s / 4,6 s | 3,9 s / 4,4 s | 0,0049 | 94,59 |
| 18 | `deepgram/nova-3 + openai/gpt-4.1` (recusas 1/27) | 4 (9) | 96,47 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,6 s / 5,6 s | 4,4 s / 5,4 s | 0,0080 | 94,59 |
| 19 | `deepgram/nova-3 + openai/gpt-4.1-mini` (recusas 2/27) | 4 (9) | 96,45 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,3 s / 4,5 s | 4,5 s / 6,3 s | 0,0050 | 94,57 |
| 20 | `deepgram/nova-3 + anthropic/claude-haiku-4.5` (recusas 1/27) | 4 (9) | 96,42 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,5 s / 5,0 s | 3,6 s / 5,7 s | 0,0069 | 94,55 |
| 21 | `deepgram/nova-3 + google/gemini-3.5-flash-lite` (recusas 1/27) | 4 (9) | 96,40 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,90 | 0,97 | 1,00 | 0,00 | 1,00 | 3,6 s / 4,4 s | 3,5 s / 4,1 s | 0,0051 | 94,52 |
| 22 | `deepgram/nova-3 + mistralai/mistral-small-3.2-24b-instruct` (recusas 0/27) | 4 (9) | 96,22 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,90 | 0,96 | 1,00 | 0,00 | 0,99 | 4,0 s / 5,6 s | 4,4 s / 9,0 s | 0,0045 | 94,62 |
| 23 | `deepgram/nova-3 + deepseek/deepseek-v4.1-flash` (recusas 1/27) | 4 (9) | 96,21 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,89 | 0,97 | 1,00 | 0,00 | 1,00 | 15,1 s / 17,3 s | 4,4 s / 5,4 s | 0,0046 | 94,34 |
| 24 | `deepgram/nova-3 + openai/gpt-4o-mini` (recusas 2/27) | 4 (9) | 96,17 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,90 | 0,96 | 1,00 | 0,00 | 0,99 | 5,1 s / 5,8 s | 4,8 s / 6,4 s | 0,0046 | 94,29 |
| 25 | `deepgram/nova-3 + meta-llama/llama-4-maverick` (recusas 1/27) | 4 (9) | 96,10 | 3,1 % | 3,1 % | 5,2 % | 1 | 0,90 | 0,96 | 1,00 | 0,00 | 0,99 | 11,1 s / 14,3 s | 17,2 s / 22,6 s | 0,0046 | 94,51 |
| 26 | `deepgram/nova-3 + qwen/qwen3.8-flash` (recusas 0/27) | 4 (9) | 95,89 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,89 | 0,95 | 1,00 | 0,00 | 0,99 | 5,6 s / 6,6 s | 5,1 s / 7,5 s | 0,0046 | 94,29 |
| 27 | `deepgram/nova-3 + openai/gpt-6-luna` (recusas 1/27) | 4 (9) | 95,79 | 2,9 % | 2,9 % | 5,2 % | 1 | 0,88 | 0,95 | 1,00 | 0,00 | 0,99 | 3,8 s / 5,6 s | 3,7 s / 4,8 s | 0,0045 | 93,91 |
| 28 | `deepgram/nova-3 + mistralai/mistral-medium-3.1` (recusas 0/27) | 4 (9) | 94,05 | 2,9 % | 2,9 % | 5,2 % | 0 | 0,83 | 0,91 | 1,00 | 0,00 | 0,99 | 3,7 s / 4,9 s | 3,6 s / 4,3 s | 0,0052 | 92,14 |
| 29 | `deepgram/nova-3 + qwen/qwen3-235b-a22b-2507` (recusas 0/12) | 0 (1) | — | 3,9 % | 3,9 % | 10,0 % | 0 | 0,78 | 0,86 | 1,00 | — | 0,99 | 4,2 s / 4,5 s | — / — | 0,0050 | — |
| 30 | `openai/gpt-transcribe + qwen/qwen3-235b-a22b-2507` (recusas 2/10) | 1 (2) | — | 6,0 % | 6,0 % | 15,0 % | 0 | 0,73 | 0,57 | 1,00 | — | 0,95 | 4,3 s / 6,3 s | — / — | 0,0060 | — |

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

- **Ditado de 20 s**: passo único do inkling, **4,2 s** p50 (5,1 s p95) do fim do áudio ao texto final, do
  Mac — ~0,9 s a mais que o `nova-3` + `gpt-4.1-mini` desclassificado (3,3 s) e ~0,4 s mais rápido que o
  `gpt-transcribe` + `claude-haiku-4.5` das duas passadas (4,6 s). Acima do alvo de ~2,5 s (NM-latencia); abaixo
  dele, sem erro de sentido, só a transcrição sozinha (`gpt-4o-mini-transcribe`, 1,6 s, nota 95,8).
- **Longos (32–52 s)**: 5,5 s p50, 7,8 s p95 — por isso o teto do passo único é 12 s
  (`FinalPass.ONE_STEP_TIMEOUT`; a transcrição + formatação segue com 8 s).
- **Custo**: passada final US$ 0,0049 por minuto de ditado; ao vivo no motor Nuvem, `gpt-4o-mini-transcribe` a
  US$ 0,0018 por minuto de áudio mais o contexto repetido das janelas (`DictationCostEstimate`: ≈ R$ 0,70 a 2,90
  por hora na nuvem, R$ 1,60 a 1,90 no aparelho com a revisão).

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
