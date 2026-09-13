# Melhorias

Aprimoramentos, refatorações e otimizações que **não** são necessários agora.
O que falta para o produto ficar completo está em
[`TAREFAS_PENDENTES.md`](TAREFAS_PENDENTES.md).

## Código

- `MainActivity.kt` ainda concentra UI e estado: separar em telas e mover o
  estado para `ViewModel`, que hoje se perde na rotação.
- O desktop tem o próprio controlador de ditado; poderia usar o
  `DictationPipeline` com dicionário e preferências em memória, sem duplicar a
  espera pela última janela.
- `androidx.security:security-crypto` foi descontinuada: migrar o cofre da chave
  para Android Keystore + Tink. Hoje uma falha passageira do Keystore ao abrir o
  cofre o recria vazio e obriga a salvar a chave de novo.
- Teste instrumentado ou Robolectric para `EncryptedSecretStore` (backup
  restaurado, arquivo corrompido).
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
- Religar o botão flutuante depois que o processo morre, sem violar a regra de
  microfone "while-in-use" (por exemplo, pela ação da notificação).
- Cofre de chave no desktop para macOS (Keychain) e Linux (Secret Service).

## Build e dependências

- O lint aponta versões novas: AGP 9.4, Gradle 8.14.5, Compose BOM 2026.09,
  core-ktx 1.19, lifecycle 2.11, kotlinx-serialization 1.9. `targetSdk` 35 → 36.
- Compose Multiplatform 1.9+ quando o Kotlin passar de 2.1.10.
- `.gitignore` tem uma linha estranha (`*!shared.keystore`) e `.kotlin/`
  duplicado.
