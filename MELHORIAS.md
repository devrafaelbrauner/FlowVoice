# Melhorias

Aprimoramentos, refatorações e otimizações que **não** são necessários agora.
O que falta para o produto ficar completo está em
[`TAREFAS_PENDENTES.md`](TAREFAS_PENDENTES.md).

## Código

- `MainActivity.kt` concentra UI, estado e orquestração (~900 linhas): separar
  em telas e mover o estado para `ViewModel`, que hoje se perde na rotação.
- `androidx.security:security-crypto` foi descontinuada: migrar o cofre da chave
  para Android Keystore direto ou Tink.
- `signInGoogle` descarta a exceção ("Falha no Google Sign-In."): registrar o
  tipo do erro no diagnóstico, sem dados pessoais.
- Serviço de acessibilidade assina `typeAllMask` com `onAccessibilityEvent`
  vazio: restringir os tipos de evento para economizar bateria.
- `FlowVoiceAccessibilityService.onStartCommand` duplica o receiver do adb e
  não é alcançável pelo shell (o serviço exige `BIND_ACCESSIBILITY_SERVICE`).
- `OpenRouterApiClient` envia `Content-Type: application/json` num GET sem
  corpo.
- `Greeting`/`GreetingTest` são placeholders do scaffold.
- Notas e dicionário ficam em SharedPreferences sem cifra: avaliar cifrar dados
  pessoais em repouso.

## Build e dependências

- O lint aponta versões novas: AGP 9.4, Gradle 8.14.5, Compose BOM 2026.09,
  core-ktx 1.19, lifecycle 2.11, kotlinx-serialization 1.9. `targetSdk` 35 → 36.
- `.gitignore` tem uma linha estranha (`*!shared.keystore`) e `.kotlin/`
  duplicado.
