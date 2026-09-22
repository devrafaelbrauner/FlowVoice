# Requisitos — FlowVoice

Escopo inicial: **Android primeiro**, **uso pessoal**, **pt-BR**, preparado
arquiteturalmente para **Windows**.

## Requisitos funcionais

1. **Ditado por voz → texto** em nuvem, exclusivamente via **OpenRouter**.
2. **Texto durante a fala**: pré-visualização ao vivo enquanto a pessoa fala.
3. **Inserção direta no campo ativo**, **mantendo o teclado atual**
   (`IntelligentKeyboard`). Sem criar teclado substituto.
4. **Gatilho**: botão flutuante + serviço de Acessibilidade.
5. **Configuração de chave OpenRouter** dentro do app, com **validação**.
6. **Dicionário pessoal** com **aprovações do usuário** (termos que o usuário
   valida/aprova para uso futuro).
7. **Área de notas**.
8. **Pontuação e ortografia** (revisão/formatação por IA).
9. **Login Google obrigatório** (Conexão via Credential Manager).
10. **Sincronização** de notas, dicionário aprovado e preferências
    (Supabase Auth + PostgreSQL, RLS). **A chave OpenRouter NÃO é sincronizada.**
11. **Idioma inicial**: português brasileiro.

## Requisitos não funcionais

1. **Versionamento Git obrigatório** (commits pequenos e atômicos).
2. **Créditos das referências** mantidos (`ATTRIBUTIONS.md`,
   `THIRD_PARTY_NOTICES.md`, `docs/references.md`).
3. **Preparado para manutenção e atualização** (arquitetura modular, teste
   automatizado, CI).
4. **Qualidade de código**: compilação limpa, testes relevantes, sem comentários
   desnecessários, respeitando convenções.
5. **Segurança**: chave OpenRouter armazenada **local e cifrada**; nunca commitar
   segredos; segredos nunca em logs.

## Prioridades de UX

1. **Inserção direta no campo correto** (prioridade máxima).
2. **Texto ao vivo** com distinção visual clara entre **texto provisório** e
   **texto finalizado**.
3. Sincronização **fora do caminho crítico** da transcrição (a latência do ditado
   nunca deve depender de rede de sync).

## Qualidade / verificação

- Compilação e testes (unitários + integração relevantes).
- **Benchmark em aparelho real** (Galaxy S26 Ultra) com critérios de
  latência, precisão pt-BR e custo.
- **Diagnóstico Técnico exportável** (estado do acesso, permissões, latências,
  erros).
- Checklist de aceite por fase.

## Plataforma / alvo

- **Dispositivo-padrão**: Samsung Galaxy S26 Ultra.
- Android nativo via Kotlin Multiplatform.
- Windows: implementado no módulo `desktopApp` (Compose Desktop + JNA/`SendInput`
  + DPAPI, fase F13); falta validar num Windows real (F13.7).

## Dados pendentes (a confirmar pelo usuário)

- Versão exata do **Android / One UI** no S26 Ultra.
- **Apps-alvo** principais do ditado (onde o texto será inserido).
- **Microfone / fone** usado no dia a dia.
- **Limite de gasto** para benchmark e uso real.
- **Confirmação final da licença** do FlowVoice (MIT proposta).