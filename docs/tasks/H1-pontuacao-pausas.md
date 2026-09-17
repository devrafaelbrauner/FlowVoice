# H1 — Pontuação, “pausas” e erros do ditado direto

**Status:** [~] medição no S26 pendente (adb vazio em 2026-09-17)

Queixa: o áudio sai com muitos erros, muitas pausas e pontuação
simples errada. Este roteiro separa três hipóteses **antes** de
ligar a revisão por IA como padrão.

Não muda captura, janela, modelo nem `prefs_json` já gravado.
Default `proofreadingEnabled` continua `false` até o par OFF/ON
no aparelho.

## Hipóteses

| Id | Hipótese | O que a confirma |
| --- | --- | --- |
| **H1** | Pontuação de janela. O modelo fecha cada trecho de 2–4 s com ponto. “Pausas” = frases picadas. A revisão no fim (já validada, P147) conserta se estiver **ligada**. | Mesmo roteiro, revisão OFF depois ON. ON deixa o campo com frase; OFF deixa ponto cedo. |
| **H2** | Corte de 450 ms parte hesitação. O texto **ao vivo** chega picado mesmo que o fim fique bom. | `dictation_window cut=pause` no meio de cláusula; a pausa *percebida* coincide com o corte. |
| **H3** | WER / emenda (palavra partida, dobro, apagar contraste). | Termo âncora errado; canário hiper/hipo some. |

## Preparo

1. Galaxy S26 Ultra no cabo. `adb devices` mostra o aparelho.
2. APK **sem** default novo: `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`
3. Log de texto (só debug): `adb shell run-as dev.rafaelbrauner.flowvoice touch files/transcript-text-logging`
4. Campo: Samsung Notes. Bolha no modo direto (“Revisar antes de inserir” **desligado**).
5. Modelo: `openai/gpt-transcribe`.

## Roteiros

**Praia** (o da P147): *Hoje o dia está muito bonito, por isso iremos para a praia pela manhã. A Praia do Futuro é a praia de escolha, pois lá tem uma água de coco bem gelada.*

**Clínico:** *Exame de sangue mostrou leucocitose. Evoluiu com episódios de diarreia e desidratação. Hipertensão. Hipotensão. Losartana.*

Falar “hipertensão” e, depois de uma pausa curta, “hipotensão” — canário P156.

## S3 — OFF depois ON (H1)

Mesma APK. Não mudar o default no código.

1. Ajustes: **Revisão por IA desligada**. Ditar o roteiro praia. Encerrar na bolha.
2. Fotografar o campo. Log: `transcription_window_text`, `dictation_direct_inserted`, `dictation_window cut= durationMs= voicedMs=`, `dictation_proofread_skipped` (esperado `reason` de desligado / pref off).
3. Ligar **Revisão por IA**. Repetir o mesmo roteiro.
4. Campo no fim: vírgula de continuação, ponto só no fim de frase. Log: `dictation_proofread_applied` **ou** `skipped` com `reason=` explícito (`demorou`, `guard`, `campo_diferente`). `drift=` se houver troca.
5. Prévia: OFF nunca mostra “revisando…”; ON mostra ≥ 1 s no fim.

**H1 ganha** se o campo ON está pontuado como fala contínua e o OFF não.
**Não** ligar o default no código neste passo.

## S5 — canário P156 (H3)

Depois do par OFF/ON, no estado de revisão que for o candidato.

1. Roteiro clínico. Conferir âncoras no campo: diarreia, hipertensão, hipotensão, losartana, desidratação, leucocitose.
2. Hipertensão e hipotensão **as duas** presentes, nessa ordem. Nenhuma vira a outra na emenda.
3. Regressão P150: uma frase com “está / tá muito bonito” **não** deve dobrar “muito bonito”.

## S6 — só se a medição pedir

- H2: anotar se `cut=pause` cai no meio da cláusula **e** a queixa ao vivo continua com revisão ON. Sem ticket de endpointing se H1 resolve o campo final.
- P157: se revisão ON deixar concordância pior que o bruto (“foram nomeado”), aí sim `ProofreadingMerge`. Senão, fora.
- P158 (CI desktop flaky): fora desta fatia.

## Aceite no cabo

- Só S26, não emulador.
- Termos âncora pass/fail, sem WER percentual inventado.
- P146 (`verbose_json` recusado) não é dependência de sucesso.

## Fora

Windows, sync, troca de modelo, auto-finalize, revisão janela a janela,
migrar `flowvoice_prefs` de quem já tem a revisão desligada.
