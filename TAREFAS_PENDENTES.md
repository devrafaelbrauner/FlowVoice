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
| P25 | `TYPE_APPLICATION_OVERLAY` exige API 26 com minSdk 24: proteger por versão ou subir o minSdk | pendente | lint |
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
| P30 | Alta | O fallback `ACTION_SET_TEXT` (`FlowVoiceAccessibilityService.kt:96-101`) substitui todo o conteúdo do campo do app-alvo pelo texto ditado. No Android 7 a 12 esse é sempre o caminho usado, e conta como sucesso | pendente | /corrigir |
| P31 | Alta | Trecho com falha (timeout, sem chave) some do texto (`LivePreviewAssembler.kt:12`) e a inserção parcial sai como sucesso, sem Toast; sem chave, a mensagem é só "sem texto para inserir" | pendente | /corrigir |
| P32 | Média | Corrida no início: `Recording` só é publicado depois de `controller.start()` (`DictationPipeline.kt:113-114`). Toque duplo gera Toast de falha, cancelar é ignorado e ocultar o botão nesse intervalo deixa a gravação sem overlay | pendente | /corrigir |
| P33 | Média | A inserção não confere o destino: ditado pela `MainActivity` com foco no campo "Google Web Client ID" grava o texto nas preferências (`MainActivity.kt:159-160`) | pendente | /corrigir |
| P34 | Média | `docs/tasks/F12.md` marca F12.4 `[x]`, mas o aceite ponta a ponta não foi observado (`chars=0` nas duas sessões; a inserção veio do receiver de debug, não do pipeline). Voltar para `[~]` até P20 | pendente | /corrigir |
| P35 | Média | Testes do pipeline não cobrem: duas sessões seguidas no singleton, start duplo, cancelar durante o start, janela com falha, inserter falhando, erro de captura, e frames vindos de outra thread | pendente | /corrigir (testes de regressão de P31/P32) |
| P36 | Média (suspeito) | `EncryptedSecretStore.reset()` chama `open()` sem proteção: se a falha for da chave-mestra, o app cai ao criar o singleton. Uma falha passageira do Keystore apaga a chave | pendente | /debugar |
| P37 | Média (suspeito) | Overlay: `stopSelf()` quando `startForeground` falha (`FlowVoiceOverlayService.kt:48-50`) e `addView` sem proteção (`:133`) podem derrubar o app | pendente | /debugar |
| P38 | Baixa | Finalize cancelado continua: com a revisão ligada, o texto vai à OpenRouter mesmo depois de cancelar (token só conferido depois de `reviseFinalText`) | pendente | /corrigir |
| P39 | Baixa | `POST_NOTIFICATIONS` só é pedida quando falta o microfone (`MainActivity.kt:784-792`); complementa P24 | pendente | /corrigir |
| P40 | Baixa | `startedHere` nunca volta a `false` no overlay: ocultar o botão cancela um ditado iniciado pela `MainActivity` | pendente | /corrigir |
| P41 | Baixa | A captura Android para sozinha quando `read` devolve ≤ 0 e a sessão continua como "● Gravando" (`AndroidAudioCaptureEngine.kt:102-105`) | pendente | /debugar |
| P42 | Baixa | Privacidade dos logs: o diagnóstico debug loga 40 caracteres do campo de outro app, `onStartCommand` de debug está no código principal e o release loga o pacote de cada app ditado | pendente | /seguranca |
| P43 | Baixa | Docs: o `AGENTS.md` não cita `desktopApp` nos comandos nem no CI; o `PLAN.md` e o `requirements.md` ainda dizem que a implementação Windows está "adiada" | pendente | /corrigir |
| P44 | Baixa | Branches auxiliares no GitHub (`docs/arquivos-controle`, `fix/achados-verificacao`, `feat/f13-windows`, `feat/ditado-overlay-app-alvo`) já integradas na `f03-audio-capture-session` | pendente: apagar após o merge do PR #2 | usuário |
| P45 | Alta | O runtime empacotado do desktop (MSI/EXE) não tem `java.net.http`, e o motor Ktor Java exige esse módulo. No app instalado a validação da chave falha ("rede ou serviço") e o ditado nunca libera. Evidência: `release` do runtime com `MODULES="java.base java.datatransfer java.xml java.prefs java.desktop java.logging jdk.crypto.ec"`, `jdeps` do `ktor-client-java-jvm` → `java.net.http`, e `suggestRuntimeModules` → `modules("java.instrument", "java.management", "java.net.http", "jdk.unsupported")` | pendente | /corrigir |
| P46 | Média | Desktop: "Iniciar ditado" fica habilitado enquanto o trecho final é transcrito (`FlowVoiceDesktopScreen.kt:88`). `reset()` apaga o ditado anterior, e a finalização antiga grava texto vazio ou da sessão nova (o controller não tem `sessionToken`) | pendente | /corrigir |
| P47 | Média | Captura desktop que falha no meio (microfone removido) só vai para o stderr (`JavaSoundAudioCaptureEngine.kt:77-83`): a sessão continua "gravando". Com `read` devolvendo 0 e a linha aberta, o laço gira sem pausa. O F13.2 diz que falhas viram `AudioCaptureException`, mas isso só vale no `start` | pendente | /corrigir |
| P48 | Média (suspeito) | `SendInput` não sinaliza bloqueio por UIPI no retorno: com um app elevado em foco, provavelmente informa "N caracteres inseridos" sem inserir. Retorno parcial não avisa, e repetir duplica. Validar em F13.7 | pendente | /debugar |
| P49 | Baixa | Desktop: "Inserir" duplicado com duplo clique durante a contagem; "Cancelar" em `Finalizing` ainda deixa o texto disponível para inserir | pendente | /corrigir |
| P50 | Baixa | `THIRD_PARTY_NOTICES.md`: Skia é BSD-3-Clause (não Apache), o Skiko embute HarfBuzz/FreeType/ICU e o MSI embute um runtime OpenJDK (GPLv2 + Classpath Exception) | pendente (F14) | /corrigir |
| P51 | Baixa | Nenhum teste exercita a chamada JNA real nem o tamanho de `WinUser.INPUT` (40 bytes no x64), e o aceite do F13.3 cobre só o planejador de teclas | pendente | /corrigir |
| P52 | Baixa | Quebra de linha vira `VK_RETURN` (envia a mensagem em chats) e Tab vai como `VK_PACKET`; o `mergeAdjacent` troca quebras por espaço quando há 2+ trechos, então o comportamento é inconsistente | pendente: decidir a política | /aprimorar |
| P53 | Baixa | Desktop: o controller mistura thread da UI e `Dispatchers.Default` sem sincronização (`jobs` em `ArrayList`, `cancelled` sem `@Volatile`, contador não atômico); se o coletor morrer, o finalize trava em "Transcrevendo…" | pendente | /corrigir |
| P54 | Baixa | `ProtectedFileSecretStore.readOpenRouterKey` captura `Exception` mas não `LinkageError`: um `UnsatisfiedLinkError` do JNA derruba o app na abertura. A chave é lida e decifrada a cada janela de áudio | pendente | /corrigir |
| P55 | Baixa | Empacotar no macOS falha: o `jpackage` recusa `app-version` 0.x e o plugin barra o JDK do Homebrew (`checkJdkVendor`). Só afeta gerar o pacote no Mac; o alvo é Windows | pendente | usuário |
