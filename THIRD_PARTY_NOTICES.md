# THIRD_PARTY_NOTICES

Licenças de dependências e software de terceiros **diretamente incluído** no
FlowVoice (bibliotecas, SDKs, modelos). Este arquivo é **cumulativo**: toda
dependência adicionada deve ter sua licença registrada aqui.

> **Estado atual (F00):** o projeto ainda não tem código de dependências.
> Abaixo está a **lista prevista** com as licenças esperadas; confirmar cada uma
> no momento de adotar a dependência (fase F01 em diante).

## Bibliotecas previstas (Kotlin Multiplatform)

| Biblioteca | Licença esperada | Observação |
| --- | --- | --- |
| Kotlin | Apache-2.0 | Linguagem base. |
| Jetpack Compose / Compose Multiplatform | Apache-2.0 | UI. |
| Ktor Client | Apache-2.0 | HTTP (OpenRouter). `ktor-client-mock 2.3.13` adotado em F04.2 só para testes JVM. `HttpTimeout` usado via plugin do próprio Ktor. |
| Kotlin Serialization | Apache-2.0 | Serialização. |
| Koin | Apache-2.0 | DI (adotado F01, 3.5.6). |
| kotlinx-coroutines-core/test | Apache-2.0 | Concorrência + testes (adotado F03.4, 1.10.1). |
| androidx.security:security-crypto | Apache-2.0 | Chave OpenRouter cifrada no Android via EncryptedSharedPreferences (adotado F04.3, 1.1.0-alpha06). |
| SQLDelight | Apache-2.0 | Persistência local. |
| JNA (Windows, F13) | LGPL-2.1 / MPL-2.0 (dual) | Interop Win32 / `SendInput`. |
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