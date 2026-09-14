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
| SEG-1 | Média | Notas, dicionário e e-mail em SharedPreferences sem cifra entravam no backup em nuvem e na transferência entre aparelhos | concluída (0.4.1): excluídos do backup (`BackupRulesTest`); cifrar em repouso segue com P14 | /corrigir |
| SEG-2 | Média | Repositório **público**: a `main` ainda tem `FlowVoiceAdbReceiver` exportado sem permissão, e havia 2 artefatos `flowvoice-debug` da `main` publicados pelo CI | parcial (0.4.1): artefatos apagados; falta o merge do PR #2, autorizado, depois do /verificar da 0.4.1 | usuário |
| SEG-3 | Baixa | O release logava no logcat o pacote de cada app em que se dita; parte do P42 | concluída (0.4.1) | /corrigir |
| SEG-4 | Baixa | Caminho de debug do `onStartCommand` no `main`; `diagnoseFocusedField` devolvia conteúdo do campo sem checar `isPassword`; parte do P42 | concluída (0.4.1): `FocusedFieldDiagnostic` | /corrigir |
| SEG-5 | Baixa | `typeAllMask` no serviço de acessibilidade com `onAccessibilityEvent` vazio | concluída (0.4.1): nenhum evento assinado; validar fallback e barra no aparelho (P65) | /corrigir |
| SEG-6 | Baixa | Tink 1.5.0 gravava e aceitava keyset em claro quando o Keystore falhava (P57) | concluída (0.4.1): Keystore direto + migração; validar a migração no S26 (P65) | /corrigir |
| SEG-7 | Baixa | A rota direta não recusava campo de senha, e o destino não era conferido entre iniciar e finalizar | concluída (0.4.1): `InsertionGuard` + `captureTarget`; validar no aparelho (P65) | /corrigir |
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

## Correções de segurança 0.4.1 (2026-09-13)

| ID | Prioridade | Tarefa | Status | Encaminhar |
| --- | --- | --- | --- | --- |
| — | — | **Status dos achados do /verificar da 0.4.1 (2026-09-13):** concluídos na 0.4.2 — P67, P91, P92, P90, P80, P83, P81, P69, P66 e P84. P82 (fallback com texto antigo) provavelmente resolvido pela P81, validar no aparelho. Restam P68, P70–P79, P85–P89 | — | — |
| P93 | Média | Se a bolha do overlay assumir um ditado de nota (P91), "Inserir" leva o texto ao campo ativo **e** à nota; o mesmo pode ocorrer com o `finalize()` das Notas quando o foco está em outro app. Separar o destino por sessão | pendente | /corrigir |
| P94 | Média | Validar no S26 a 0.4.2: barra ao ditar pelo Início (P91), ditado de nota com troca de aba (P67), recusa e nova tentativa (P80/P83), sessão longa parando no teto (P92), ícone abrindo o app depois das Configurações (P90), orientação de configurações restritas (P66) e TalkBack (P69) | pendente | usuário / /verificar |
| P64 | Baixa | Remover `androidx.security:security-crypto` (e o tink-android que ele traz) e o código de migração do cofre antigo numa versão futura, quando nenhum aparelho tiver mais `flowvoice_secrets.xml` | pendente | /aprimorar |
| P65 | Média | Validar no S26: (1) migração da chave para `flowvoice_vault` (Ajustes mostra CONFIGURADA, logcat `legacy_vault_Migrated`, `flowvoice_secrets.xml` removido); (2) fallback `ACTION_SET_TEXT` e barra acima do teclado sem eventos de acessibilidade (SEG-5); (3) recusa em campo de senha e quando o foco muda de app (SEG-7) | parcial (2026-09-13, S26 0.4.1): (1) confirmada — `flowvoice_secrets.xml` removido e `flowvoice_vault.xml` com `openrouter_key_iv` (16) e `openrouter_key_ct` (120); serviço conecta sem tipos de evento (`Bound services`, `eventTypes=`, `flags=0x8041`), sem crash. Faltam (2) e (3) | usuário / /verificar |
| P67 | Bloqueante | Ditado de nota órfão: trocar de aba ou girar a tela durante o ditado recria o `NotesScreenState` (`remember` em `NotesRoute.kt:79`, sem `configChanges`). O microfone continua gravando e enviando áudio à OpenRouter sem nenhum caminho para parar: a bolha e o Início recusam com o pipeline ocupado, e ocultar o botão só cancela sessões `startedHere`. Se a troca ocorrer em "Transcrevendo", o texto ditado se perde | pendente | /corrigir |
| P68 | Média | Testes de `androidApp` cobrem só lógica pura: sem teste de Compose nem Robolectric para posse da sessão nas Notas, `owned`/`dismissed` do overlay, `DictationStarter`, fluxos do `FlowVoiceRoot` e posicionamento da barra | pendente | /aprimorar |
| P69 | Média | TalkBack: toggles "Botão flutuante" e "Revisão por IA" sem nome (`SettingsRow` não mescla semântica, `FvToggle` sem rótulo); campos de título, corpo, novo termo e chave com placeholder solto em vez de rótulo | pendente | /corrigir |
| P70 | Baixa | Waveform com a onda invertida: `StartOffsetType.FastForward` (`Waveform.kt:54`) adianta cada barra, e o CSS atrasa (`animation-delay`); usar `Delay` | pendente | /corrigir |
| P71 | Baixa | Barra 1b diverge do README sem registro no F12.5: sem "voltar ao app", rótulos de rota diferentes, cronômetro em peso 400 em vez de 500 | pendente | /corrigir |
| P72 | Baixa | Textos fixos que podem ser falsos: "Relatório exportado ✓" mesmo com o compartilhamento cancelado; "Rota principal commitText" fixo com minSdk 24; dicas fixas em Ajustes ("Rota commitText ativa", "Padrão escolhido no benchmark F05"); "CONFIGURADA" em vez de "VÁLIDA · hh:mm" | pendente | /corrigir |
| P73 | Baixa | Ditado na nota trata todo o texto ao vivo como provisório e não aplica o dicionário (`NotesRoute.kt:120-121`); o overlay já separa com `liveText()` | pendente | /corrigir |
| P74 | Baixa | Saídas de permissão: microfone negado de vez no onboarding não leva aos ajustes do app; o microfone do Início não confere a chave e só falha no fim | pendente | /corrigir |
| P75 | Baixa | Navegação: o passo "Chave" do onboarding abre Ajustes com a barra inferior, e tocar numa aba descarta o onboarding sem gravá-lo; "← ajustes" volta para o Início quando o Diagnóstico foi aberto pela pílula | pendente | /corrigir |
| P76 | Baixa | Custo: o overlay recompõe a cada 200 ms (o cronômetro muda a cada 1 s) e o serviço chama `getWindows()` na thread principal a cada 400 ms, inclusive em `Ready` sem limite | pendente | /aprimorar |
| P77 | Média (suspeito) | Barra do overlay provavelmente flutua acima do teclado com folga igual à barra de navegação: `y = altura da tela − topo do teclado` é aplicado num quadro que já exclui a navegação; o fallback dobra a folga (`FlowVoiceOverlayService.kt:225-250`) | pendente: confirmar no aparelho (P58) | /debugar |
| P78 | Baixa (suspeito) | Vão acima do teclado nas abas: `imePadding()` no conteúdo com a barra inferior como irmã na `Column` (`FlowVoiceRoot.kt`) | pendente: confirmar no aparelho | /debugar |
| P79 | Baixa (suspeito) | O anel de pulso da bolha (84 dp) deve sair cortado na janela `WRAP_CONTENT` (~76 dp) | pendente: confirmar no aparelho | /debugar |
| P91 | Bloqueante | Sessão iniciada pelo microfone do Início (`ACTION_START_DICTATION`, 22:29:58 no S26) ficou com o overlay em modo bolha: `dumpsys window` com `(45,360)(wrapxwrap) gr=BOTTOM END` e nenhuma barra na tela. O microfone gravou sem controle visível; a bolha ignora toques com o pipeline ocupado e só "Ocultar botão" na notificação para. `applyMode` não recria a view; causa ainda não confirmada (hipótese: `applyMode` grava `mode = Bar` antes de `updateLayout`, que sai sem aplicar se a view ainda não está anexada) | em andamento (CB investiga antes de corrigir) | /debugar → /corrigir |
| P92 | Alta | Teto de requisições por sessão (`DEFAULT_MAX_REQUESTS_PER_SESSION = 30`): depois de 30 janelas (~2 min) o `IncrementalTranscriptionController` só registra `transcription_budget` e a captura continua para sempre, sem aviso nem texto novo. No S26 foram mais de 101 janelas (~7 min) com o microfone aberto | em andamento (CB) | /corrigir |
| P90 | Alta | Telas do sistema abertas na tarefa do FlowVoice: `startActivitySafely` e as chamadas diretas abrem `Settings.ACTION_*` sem `FLAG_ACTIVITY_NEW_TASK` (`ContextExt.kt:15`, `OnboardingRoute.kt:74`, `DiagnosticsRoute.kt:408`, `SettingsRoute.kt:570-574`, `HomeRoute.kt:147`). Depois de tocar em "Serviço de acessibilidade" no onboarding, o ícone do app reabre a tela de acessibilidade em vez do app. Evidência no S26: task 3008 com `baseActivity=MainActivity`, `topActivity=Settings$AccessibilitySettingsActivity`, `numActivities=2`, e o START do launcher com `result code=2` | pendente | /corrigir |
| P80 | Alta | SEG-7: na recusa (foco mudou de app ou campo de senha) a mensagem promete "texto mantido na barra", mas `insertReady` troca `Ready` por `Completed` com falha e o overlay esconde o resultado em 4 s, sem reinserir nem copiar: o texto ditado se perde. Manter `Ready` na recusa | pendente | /corrigir |
| P81 | Média | SEG-5: sem tipos de evento, o cache de janelas do cliente de acessibilidade não é invalidado (`TYPE_WINDOWS_CHANGED` descartado com `mUsesAccessibilityCache=false`); a barra pode ficar presa na posição do teclado da primeira leitura. Correção candidata: `setCacheEnabled(true)` no `onServiceConnected` (API 33+) ou `clearCache()` antes de `windows` | pendente: validar no aparelho | /corrigir |
| P82 | Média (suspeito) | SEG-5: pelo mesmo cache, o fallback `ACTION_SET_TEXT` pode ler texto antigo do campo e sobrescrever o que o usuário digitou depois (classe do P30). Mesma correção do P81 | pendente: confirmar no aparelho | /debugar |
| P83 | Média | SEG-7: ditado iniciado pelo Início não tem conferência de destino (`captureTarget` roda com o FlowVoice em primeiro plano e grava `null`, que nunca bloqueia); o CHANGELOG 0.4.1 afirma que a inserção é recusada se o foco mudou. Recapturar no `finalizeForReview` e comparar no `insertReady` | pendente | /corrigir |
| P84 | Média | O CI não roda `:androidApp:testDebugUnitTest`: `BackupRulesTest` e os demais testes de tela, componentes e overlay só rodam localmente | pendente | /corrigir |
| P85 | Baixa | SEG-7: a conferência compara pacote, não campo (mesmo app, outra conversa ou prontuário, passa) | pendente | /aprimorar |
| P86 | Baixa | SEG-7: o fallback detecta senha só por `node.isPassword` (transformação), deixando passar `VISIBLE_PASSWORD` e senha com "mostrar" ligado; nas APIs 26–32 o fallback é a única rota | pendente | /corrigir |
| P87 | Baixa | SEG-6: migração e leituras de chave rodam na thread principal (Koin `createdAtStart` → pipeline → `SecretStore`); com `Kept` a migração se repete a cada início de processo; cada `readOpenRouterKey` é uma operação de Keystore | pendente | /aprimorar |
| P88 | Baixa (suspeito) | SEG-6: uma migração que termina em `Kept`, seguida de uma chave B salva no cofre novo e de uma leitura transitória `null`, grava a chave antiga A por cima de B | pendente | /corrigir |
| P89 | Baixa (suspeito) | SEG-6: alias do Keystore existente mas inutilizável (`UnrecoverableKeyException`/`InvalidKeyException` persistentes) trava o cofre para sempre; só limpando os dados do app | pendente | /debugar |
| P66 | Alta | Instalado fora de loja (adb/APK), o Android 13+ bloqueia ligar o serviço de acessibilidade ("configurações restritas"): o interruptor não liga, só sobra o atalho, e o app fica sem inserção nem barra. Nem o onboarding nem o README orientam "Configurações → Aplicativos → FlowVoice → ⋮ → Permitir configurações restritas". Evidência no S26 (Android 16): `ACCESS_RESTRICTED_SETTINGS: default; rejectTime` logo após a tentativa, `initiatingPackageName=com.android.shell`, `enabled_accessibility_services=null` e o FlowVoice só em `accessibility_button_targets` | pendente: contornado no S26 com `appops set … ACCESS_RESTRICTED_SETTINGS allow` + `settings put secure enabled_accessibility_services` (rodado pelo usuário); falta orientar no onboarding e no README | /corrigir (onboarding + README) |
