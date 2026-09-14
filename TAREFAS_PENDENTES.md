# Tarefas pendentes

Backlog do que falta para o FlowVoice ficar completo. O detalhamento por fase
continua em [`docs/tasks/`](docs/tasks/README.md); aqui fica a visão priorizada.
Itens concluídos são marcados, não apagados.

**Status:** pendente · em andamento · bloqueado (depende do usuário) · concluída

## Alta

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P01 | Commitar e publicar F04–F12 (um commit por fase, PR #2) | concluída (0.2.0) | checkout local sem commit |
| P02 | `commitText` com `newCursorPosition = 0` deixava o cursor antes do texto | concluída (0.3.0) | /verify |
| P03 | `FlowVoiceAdbReceiver` exportado sem permissão | concluída (0.3.0) | /verify |
| P04 | `allowBackup` restaurava `flowvoice_secrets` sem a chave do Keystore | concluída (0.3.0) | revisão |
| P05 | Ditado pelo botão flutuante sem sair do app-alvo | concluída (0.3.0) | revisão de F12 |
| P06 | Backend de sync real (Supabase Auth + Postgres com RLS) no lugar de `InMemoryRemoteSync` | bloqueado: precisa do projeto Supabase (URL e chave anon) | F11 |
| P07 | Login validado no backend (ID token do Google trocado por sessão Supabase); hoje o `id` do usuário é o e-mail | bloqueado: depende de P06 e do Web Client ID do Google Cloud | F10 |
| P08 | Decidir a licença: `AGENTS.md` propõe MIT, mas os demais projetos do autor são proprietários | bloqueado: decisão do usuário | F00 / F14 |
| P20 | Validar no S26 com fala real: botão flutuante → texto inserido no app-alvo, com dois ditados seguidos na ordem certa | pendente: precisa de alguém falando perto do aparelho | F12.4 |
| P21 | Corrida no finalize: a última janela chegava à fila depois do `awaitIdle` | concluída (0.3.0) | F13 / F12.4 |

## Média

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P09 | `ModelInfo.contextLength` sempre nulo | concluída (0.3.0) | /verify |
| P10 | Rodar `lintDebug` no CI | concluída (0.3.0) | /verify |
| P11 | F13 — preparação para Windows (Compose Desktop, JNA `SendInput`, DPAPI) | concluída (0.3.0), exceto F13.7 | PLAN |
| P12 | F13.7 — validar num Windows real: inserção (emoji, acentos, UIPI), DPAPI, microfone, `packageMsi` | pendente: precisa de um Windows | F13 |
| P13 | F14 — higiene de licença e entrega: `LICENSE`, notices finais, documentação de release | pendente (depende de P08) | PLAN |
| P14 | Persistência em SQLDelight/SQLite, como decidido no PLAN (hoje notas e dicionário são JSON em SharedPreferences) | pendente | PLAN |
| P15 | Evidência em aparelho real de F06 a F11 (os docs dessas fases não registram validação no S26 Ultra) | pendente | docs/tasks |
| P16 | Docs desatualizados (AGENTS, F01, F02, PLAN) | concluída (0.3.0) | /verify |
| P17 | `README.md` com instalação, configuração e build | concluída (0.3.0) | revisão |
| P22 | Desktop: atalho global e janela flutuante que não rouba o foco (hoje minimiza e insere após 3 s) | pendente | F13 |
| P23 | Definir `upgradeUuid` e `menuGroup` do MSI antes do primeiro instalador | pendente | F13 |
| P24 | Onboarding: tratar recusa de `POST_NOTIFICATIONS` (a notificação do serviço de microfone some) | pendente | F12.4 |
| P25 | `TYPE_APPLICATION_OVERLAY` exige API 26 com minSdk 24: proteger por versão ou subir o minSdk | concluída (0.3.1, com P37): sem overlay e sem crash abaixo do Android 8; o aviso `InlinedApi` do lint continua | lint |
| P26 | `stopService` com instância nova em `MainActivity` (`ImplicitSamInstance` no lint) pode não parar o overlay | pendente | lint |

## Baixa

| ID | Tarefa | Status | Origem |
| --- | --- | --- | --- |
| P18 | Regressão do F02 em apps-alvo adicionais (WhatsApp, Chrome, Obsidian) | pendente | PLAN |
| P19 | Ícone do app (`MissingApplicationIcon` no lint) | pendente | lint |
| P27 | Botão flutuante arrastável, com posição lembrada (hoje cobre a tecla de ação do teclado) | pendente | F12.4 |
| P28 | `GoogleSignInHelper`: tratar `NoCredentialException` e declarar `<queries>` para `resolveActivity` | pendente | lint |
| P29 | Levar dicionário, revisão e notas ao desktop | pendente | F13 |

## Achados da verificação de `9d527d7` (2026-09-13)

| ID | Prioridade | Tarefa | Status | Encaminhar |
| --- | --- | --- | --- | --- |
| P30 | Bloqueante | O fallback `ACTION_SET_TEXT` substituía todo o conteúdo do campo do app-alvo pelo texto ditado | concluída (0.3.1): insere no cursor; recusa em senha, texto ilegível e Android < 8 | /corrigir |
| P31 | Alta | Trecho com falha sumia do texto e a inserção parcial saía como sucesso, sem aviso | concluída (0.3.1) | /corrigir |
| P32 | Média | Corrida no início do ditado: toque duplo, cancelar e ocultar durante o `start` | concluída (0.3.1) | /corrigir |
| P33 | Média | A inserção não conferia o destino: ditado pela `MainActivity` gravava nos campos do próprio app | concluída (0.3.1) | /corrigir |
| P34 | Média | F12.4 marcada `[x]` sem aceite ponta a ponta | concluída (0.3.1): volta para `[~]` até P20 | /corrigir |
| P35 | Média | Testes do pipeline sem sessões seguidas, start duplo, falha de trecho, inserção falha, erro de captura e frames de outra thread | concluída (0.3.1) | /corrigir |
| P36 | Média | `EncryptedSecretStore`: com a chave-mestra inutilizável o app caía em loop ao abrir (`InstanceCreationException`); uma falha passageira do Keystore apagava a chave. Diagnóstico /debugar: confirmado (hipóteses 1 e 2a; 2b refutada) | concluída (0.3.1): `SecretVaultOpener`; a checagem da chave-mestra no Keystore não tem teste automatizado | /corrigir |
| P37 | Média | Overlay: `addView` sem proteção lançava `BadTokenException` sem permissão de sobreposição (ou na API 24–25), e `onDestroy` removia view não anexada. Diagnóstico /debugar: hipótese do `stopSelf` refutada, `addView` confirmado | concluída (0.3.1): `OverlayStartGuard`; caminho de exceção do `addView` só com teste manual (F12.md) | /corrigir |
| P38 | Baixa | Finalize cancelado continua: com a revisão ligada, o texto vai à OpenRouter mesmo depois de cancelar | pendente | /corrigir |
| P39 | Baixa | `POST_NOTIFICATIONS` só é pedida quando falta o microfone; complementa P24 | pendente | /corrigir |
| P40 | Baixa | `startedHere` nunca volta a `false` no overlay: ocultar o botão cancela um ditado iniciado pela `MainActivity` | pendente | /corrigir |
| P41 | Baixa | A captura Android parava sozinha quando `read` devolvia ≤ 0 e a sessão seguia "● Gravando" | concluída (0.3.1, junto com P47) | /corrigir |
| P42 | Baixa | Privacidade dos logs: o diagnóstico debug loga 40 caracteres do campo de outro app, `onStartCommand` de debug no código principal e o release loga o pacote de cada app ditado | auditado: dividido em SEG-3 e SEG-4 | /corrigir |
| P43 | Baixa | Docs: o `AGENTS.md` não cita `desktopApp` nos comandos nem no CI; o `PLAN.md` e o `requirements.md` ainda dizem que a implementação Windows está "adiada" | pendente | /corrigir |
| P44 | Baixa | Branches auxiliares no GitHub já integradas na `f03-audio-capture-session` | pendente: apagar após o merge do PR #2 | usuário |
| P45 | Alta | O runtime empacotado do desktop não tinha `java.net.http` (motor Ktor Java) | concluída (0.3.1); o CI confere o `release` do runtime | /corrigir |
| P46 | Média | Desktop: iniciar ou cancelar durante a transcrição final apagava ou misturava o texto de outra sessão | concluída (0.3.1) | /corrigir |
| P47 | Média | Captura desktop que falhava no meio só ia para o stderr; `read` = 0 com a linha aberta girava sem pausa | concluída (0.3.1) | /corrigir |
| P48 | Média (suspeito) | `SendInput` não sinaliza bloqueio por UIPI no retorno; retorno parcial não avisa e repetir duplica. Validar em F13.7 | pendente | /debugar |
| P49 | Baixa | Desktop: "Inserir" duplicado com duplo clique durante a contagem; "Cancelar" em `Finalizing` liberava o texto | parcial (0.3.1): o cancelamento na finalização foi coberto por P46; falta travar a contagem de inserção | /corrigir |
| P50 | Baixa | `THIRD_PARTY_NOTICES.md`: Skia é BSD-3-Clause, o Skiko embute HarfBuzz/FreeType/ICU e o MSI embute um runtime OpenJDK (GPLv2 + Classpath Exception) | pendente (F14) | /corrigir |
| P51 | Baixa | Nenhum teste exercita a chamada JNA real nem o tamanho de `WinUser.INPUT` (40 bytes no x64) | pendente | /corrigir |
| P52 | Baixa | Quebra de linha vira `VK_RETURN` (envia a mensagem em chats) e Tab vai como `VK_PACKET`; `mergeAdjacent` troca quebras por espaço com 2+ trechos | pendente: decidir a política | /aprimorar |
| P53 | Baixa | Desktop: o controller mistura thread da UI e `Dispatchers.Default` sem sincronização (`jobs`, `cancelled`, contador) | pendente | /corrigir |
| P54 | Baixa | `ProtectedFileSecretStore.readOpenRouterKey` não captura `LinkageError` (JNA) e decifra a chave a cada janela | pendente | /corrigir |
| P55 | Baixa | Empacotar no macOS falha: `jpackage` recusa `app-version` 0.x e o plugin barra o JDK do Homebrew | pendente | usuário |
| P56 | Baixa | Depois de P30, o fallback recusa no Android 7 (sem `isShowingHintText`) e em campos que reportam texto nulo: nesses casos não há inserção (antes apagava o campo) | pendente: avaliar se algum app-alvo real cai nisso | /debugar |
| P57 | Média | Tink 1.5.0 pode gravar o keyset **em claro** quando não consegue gerar a chave-mestra (`readOrGenerateNewMasterKey`), sem avisar | confirmado pelo /seguranca (SEG-6, rebaixado a Baixa: exige falha rara do Keystore e acesso ao sandbox) | /corrigir |

## Achados da auditoria /seguranca de `9722166` (2026-09-13)

Veredito: com ressalvas (0 críticos, 0 altos). gitleaks (histórico), semgrep (`p/kotlin`, `p/secrets`, `p/owasp-top-ten`), OSV API e lint Security sem achados confirmados.

| ID | Severidade | Tarefa | Status | Encaminhar |
| --- | --- | --- | --- | --- |
| SEG-1 | Média | Notas, dicionário e e-mail em SharedPreferences sem cifra; só `flowvoice_secrets.xml` sai do backup, então notas (uso clínico, LGPD) vão para o backup em nuvem e a transferência entre aparelhos (`data_extraction_rules.xml`, `backup_rules.xml`, `Prefs*Persist.kt`) | pendente: decidir se ficam no backup; cifrar em repouso junto com P14 | usuário, depois /corrigir |
| SEG-2 | Média | Repositório **público**: a `main` ainda tem `FlowVoiceAdbReceiver` exportado sem permissão, e há 2 artefatos `flowvoice-debug` da `main` publicados pelo CI (expiram em 2026-12-12) | pendente: mesclar o PR #2 e apagar os artefatos antigos | usuário |
| SEG-3 | Baixa | O release loga no logcat o pacote de cada app em que se dita (`FlowVoiceAccessibilityService.kt:95`); parte do P42 | pendente | /corrigir |
| SEG-4 | Baixa | Caminho de debug do `onStartCommand` no source set `main`; `diagnoseFocusedField` devolve 40 caracteres do campo de qualquer app sem checar `isPassword`; parte do P42 | pendente | /corrigir |
| SEG-5 | Baixa | `typeAllMask` no serviço de acessibilidade com `onAccessibilityEvent` vazio: recebe eventos de todos os apps sem necessidade (`accessibility_flowvoice.xml:3`) | pendente | /corrigir |
| SEG-6 | Baixa | Tink 1.5.0 grava e aceita keyset em claro quando o Keystore falha (P57); `security-crypto` descontinuado | pendente | /corrigir |
| SEG-7 | Baixa | A rota direta `commitText` não recusa campo de senha, e o destino não é conferido entre iniciar e finalizar: se o foco mudar durante a transcrição, o texto cai em outro app | pendente | /corrigir |
| SEG-8 | Info | `SyncModels.kt:15`: a trava `contains("sk-")` derruba o sync com termo legítimo e não cobre notas | pendente | /aprimorar |
| SEG-9 | Info | Nenhuma tela usa `filterTouchesWhenObscured` (tapjacking); avaliar nas telas de chave e permissões | pendente | /aprimorar |

## Redesign 0.4.0 (2026-09-13)

| ID | Prioridade | Tarefa | Status | Encaminhar |
| --- | --- | --- | --- | --- |
| P58 | Média | Conferir no S26 o visual das telas e da barra de ditado contra as capturas do handoff (`~/Downloads/design_handoff_flowvoice_app/screenshots`), incluindo a barra ancorada acima do teclado e o tema claro | pendente: o aparelho estava em uso na entrega | usuário / /verificar |
| P59 | Baixa | Estatísticas do Início (latência média, ditados hoje, gasto hoje) e latência p50/p95 do Diagnóstico sem fonte persistida: aparecem como "—" | pendente | /construir |
| P60 | Baixa | O dicionário não guarda a forma ouvida nem o contexto ("brauner → Brauner"); os termos pendentes ficam só em memória e somem ao reiniciar | pendente | /construir |
| P61 | Baixa | "sincronizado" por nota e "Limite de gasto" do design não têm funcionalidade por trás; omitidos | pendente (depende de P06) | /construir |
| P62 | Baixa | Logo do Google no login e ícone do app ainda são placeholders (ver P19) | pendente | usuário |
| P63 | Baixa | `flagRetrieveInteractiveWindows` (para ancorar a barra acima do teclado) amplia o que o serviço de acessibilidade pode ler; revisar junto com SEG-5 | pendente | /seguranca |
