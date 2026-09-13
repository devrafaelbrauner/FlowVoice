# Referências

Projetos e APIs avaliados para o design do FlowVoice. Este registro existe para
responder, de forma auditável, **o que inspirou** o projeto e **o que NÃO foi
copiado** (crítico para a higiene de licença).

Regra de ouro: **nenhum código é copiado** de projetos GPL/AGPL. A inspiração é
de arquitetura e de UX. Isso deve ser mantido para qualquer referência futura.

## Projetos de referência (código aberto)

| Projeto | Commit avaliado | Licença | Uso no FlowVoice |
| --- | --- | --- | --- |
| `Open-Less/openless` | `ef78a091a949` | AGPL-3.0 | Padrões de serviço de Acessibilidade, botão flutuante/sobreposição e inserção de texto em Android. Somente referência — **nada copiado**. |
| `cjpais/handy` | `2bdf9ac05724` | MIT | Abordagem de digitação por voz. Referência de UX. |
| `voquill/voquill` | `ef8572a3b00d` | AGPLv3 (+ termos) | Experiência de voz em contexto de IME no mobile. Somente referência — **nada copiado**. |
| `altic-dev/FluidVoice` | `42e33e68ec47` | GPLv3 | Ditado ao vivo. Somente referência — **nada copiado**. |
| `OpenWhispr/openwhispr` | `4335972b96a6` | MIT | Processamento de voz. Referência de UX. |
| `zachlatta/freeflow` | `ad5c827b5a32` | MIT | Fluxo de ditado. Referência de UX. |

## Referência pessoal do autor

| Projeto | Licença | Uso no FlowVoice |
| --- | --- | --- |
| `devrafaelbrauner/intelligent-keyboard` | FUTO Source First License 1.1-kb | Teclado atual a manter (`IntelligentKeyboard`). Fonte de corpus de ditado pt-BR e de medições (`docs/voz-ptbr-medicao.md`). Avaliação e benchmark apenas; reuso de código sujeito à licença FUTO. |

Arquivos de referência mais úteis no `intelligent-keyboard`:

- `docs/voz-ptbr-medicao.md` — metodologia de medição de ditado em pt-BR.
- `java/src/org/futo/inputmethod/latin/LatinIME.kt` — IME base.
- `java/src/org/futo/inputmethod/latin/ai/OpenRouterProvider.kt` — integração OpenRouter.
- `java/src/org/futo/inputmethod/latin/uix/voice/DictationSession.kt` — sessão de ditado / captura de áudio.

## APIs e documentação externa

- OpenRouter — endpoint de transcrição: `POST https://openrouter.ai/api/v1/audio/transcriptions`
- OpenRouter — catálogo de modelos de transcrição: `GET https://openrouter.ai/api/v1/models?output_modalities=transcription`
- Android — `InputMethod` (serviço de Acessibilidade com conexão de edição): `https://developer.android.google.cn/reference/android/accessibilityservice/InputMethod`
- Android — `InputMethod.AccessibilityInputConnection` (`commitText`, `setSelection`): `https://developer.android.google.cn/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection`

## Notas de licença

- Para reuso de **código** de qualquer referência, a licença daquele código deve
  ser compatível com a licença do FlowVoice (MIT proposta) e registrada em
  `THIRD_PARTY_NOTICES.md`.
- Projetos **GPL/AGPL** (`openless`, `voquill`, `FluidVoice`): **proibido** copiar
  código. Apenas referência de ideas/padrões.