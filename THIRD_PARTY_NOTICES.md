# THIRD_PARTY_NOTICES

Licenças de dependências e software de terceiros **diretamente incluído** no
FlowVoice (bibliotecas, SDKs, modelos). Este arquivo é **cumulativo**: toda
dependência adicionada deve ter sua licença registrada aqui.

## Bibliotecas adotadas

| Biblioteca | Versão | Licença | Observação |
| --- | --- | --- | --- |
| Kotlin | 2.1.10 | Apache-2.0 | Linguagem base. |
| Jetpack Compose BOM | 2025.06.00 | Apache-2.0 | UI Android. |
| Ktor Client | 2.3.13 | Apache-2.0 | HTTP (OpenRouter). |
| Kotlin Serialization | 1.7.3 | Apache-2.0 | JSON. |
| kotlinx-coroutines | 1.10.1 | Apache-2.0 | Concorrência. |
| Koin | 3.5.6 | Apache-2.0 | DI. |
| androidx.security:security-crypto | 1.0.0 | Apache-2.0 | Só para migrar a chave do cofre antigo (`flowvoice_secrets`) para o cofre com Android Keystore direto; remover quando a migração não for mais necessária. |
| androidx.credentials | 1.3.0 | Apache-2.0 | Google Sign-In (F10). |
| googleid | 1.1.1 | Apache-2.0 | Google ID token (F10). |
| JNA (`net.java.dev.jna:jna`) | 5.19.1 | Apache-2.0 OR LGPL-2.1-or-later (dupla; usada sob Apache-2.0) | Interop nativa no desktop (F13). |
| JNA Platform (`net.java.dev.jna:jna-platform`) | 5.19.1 | Apache-2.0 OR LGPL-2.1-or-later (dupla; usada sob Apache-2.0) | Win32 `SendInput` e DPAPI (`Crypt32Util`) (F13). |
| Compose Multiplatform (`org.jetbrains.compose`) | 1.8.2 | Apache-2.0 | UI desktop (`desktopApp`, F13); inclui Skiko/Skia nativos. |
| kotlinx-coroutines-swing | 1.10.1 | Apache-2.0 | `Dispatchers.Main` no desktop (F13). |
| sherpa-onnx (`com.github.k2-fsa.sherpa-onnx:sherpa-onnx`, AAR) | 1.13.8 | Apache-2.0 | Motor de transcrição no aparelho (Android). O AAR oficial do release do GitHub, servido pelo JitPack e preso por SHA-256 em `gradle/verification-metadata.xml`. https://github.com/k2-fsa/sherpa-onnx |
| ONNX Runtime (`libonnxruntime.so`, dentro do AAR do sherpa-onnx) | a do sherpa-onnx 1.13.8 | MIT | Inferência do modelo no aparelho. https://github.com/microsoft/onnxruntime |
| Apache Commons Compress | 1.28.0 | Apache-2.0 | Extrair o `.tar.bz2` do modelo no aparelho. |

## Fontes empacotadas

| Fonte | Versão / origem | Licença | Observação |
| --- | --- | --- | --- |
| Instrument Sans (Regular, Medium, SemiBold, Bold) | `Instrument/instrument-sans` @ `7fa22308a3d0`, `fonts/ttf/` estáticas | SIL OFL 1.1 | UI do app Android (redesign F12). Texto da licença em `androidApp/src/main/assets/licenses/InstrumentSans-OFL.txt`. |
| JetBrains Mono (Regular, Medium, Bold) | `JetBrains/JetBrainsMono` tag `v2.304`, `fonts/ttf/` estáticas | SIL OFL 1.1 | Rótulos, dados e logs do app Android. Texto da licença em `androidApp/src/main/assets/licenses/JetBrainsMono-OFL.txt`. |

## Modelos baixados em tempo de uso (não empacotados)

| Modelo | Origem | Licença | Observação |
| --- | --- | --- | --- |
| NVIDIA Nemotron 3.5 ASR Streaming 0.6B (560 ms, int8), pacote do sherpa-onnx de 2026-06-11 | https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models (`sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11.tar.bz2`, 475 MB, SHA-256 `c6bf5e0d…ae3a`) | OpenMDW-1.1 | Baixado pelo próprio usuário em Ajustes ou no passo 3 do onboarding; fica em `filesDir`, fora do backup. Não vai no APK nem no repositório. |

## Bibliotecas previstas

| Biblioteca | Licença esperada | Observação |
| --- | --- | --- |
| SQLDelight | Apache-2.0 | Persistência local. |
| Supabase SDK | MIT (agora) | Auth + Postgres. |

## Serviços remotos (nenhum código no repo; sujeito aos Termos do provedor)

- **OpenRouter** (https://openrouter.ai) — Termos de Serviço / Política
  (não é código de terceiros no repo; API cloud).
- **Supabase** — Termos de Serviço.
- **Google** (Sign-In, Play/Android) — Termos aplicáveis.
- **GitHub** — só o download do modelo do motor no aparelho (release do k2-fsa/sherpa-onnx),
  uma vez, quando o usuário pede.

## Regras

1. Ao adicionar uma dependência, **registrar** nome, versão e licença aqui.
2. Sem dependências sob licença **incompatível com MIT** (ex.: AGPL/GPL) sem
   revisão explícita de licença.
3. Modelos de IA usados via API (OpenRouter) **não** são código no repo; a
   escolha de modelo e custo fica documentado em `docs/PLAN.md`.

## Pendências

- [ ] Confirmar licença final do FlowVoice (MIT proposta).
- [ ] Preencher versões reais de cada dependência na adopcção (F01+).