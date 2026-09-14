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
| androidx.security:security-crypto | 1.0.0 | Apache-2.0 | Chave OpenRouter cifrada (F04). |
| androidx.credentials | 1.3.0 | Apache-2.0 | Google Sign-In (F10). |
| googleid | 1.1.1 | Apache-2.0 | Google ID token (F10). |
| JNA (`net.java.dev.jna:jna`) | 5.19.1 | Apache-2.0 OR LGPL-2.1-or-later (dupla; usada sob Apache-2.0) | Interop nativa no desktop (F13). |
| JNA Platform (`net.java.dev.jna:jna-platform`) | 5.19.1 | Apache-2.0 OR LGPL-2.1-or-later (dupla; usada sob Apache-2.0) | Win32 `SendInput` e DPAPI (`Crypt32Util`) (F13). |
| Compose Multiplatform (`org.jetbrains.compose`) | 1.8.2 | Apache-2.0 | UI desktop (`desktopApp`, F13); inclui Skiko/Skia nativos. |
| kotlinx-coroutines-swing | 1.10.1 | Apache-2.0 | `Dispatchers.Main` no desktop (F13). |

## Fontes empacotadas

| Fonte | Versão / origem | Licença | Observação |
| --- | --- | --- | --- |
| Instrument Sans (Regular, Medium, SemiBold, Bold) | `Instrument/instrument-sans` @ `7fa22308a3d0`, `fonts/ttf/` estáticas | SIL OFL 1.1 | UI do app Android (redesign F12). Texto da licença em `androidApp/src/main/assets/licenses/InstrumentSans-OFL.txt`. |
| JetBrains Mono (Regular, Medium, Bold) | `JetBrains/JetBrainsMono` tag `v2.304`, `fonts/ttf/` estáticas | SIL OFL 1.1 | Rótulos, dados e logs do app Android. Texto da licença em `androidApp/src/main/assets/licenses/JetBrainsMono-OFL.txt`. |

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

## Regras

1. Ao adicionar uma dependência, **registrar** nome, versão e licença aqui.
2. Sem dependências sob licença **incompatível com MIT** (ex.: AGPL/GPL) sem
   revisão explícita de licença.
3. Modelos de IA usados via API (OpenRouter) **não** são código no repo; a
   escolha de modelo e custo fica documentado em `docs/PLAN.md`.

## Pendências

- [ ] Confirmar licença final do FlowVoice (MIT proposta).
- [ ] Preencher versões reais de cada dependência na adopcção (F01+).