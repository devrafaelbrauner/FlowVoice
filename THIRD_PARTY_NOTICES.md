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
| Ktor Client | Apache-2.0 | HTTP (OpenRouter). |
| Kotlin Serialization | Apache-2.0 | Serialização. |
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