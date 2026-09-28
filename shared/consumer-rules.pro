# sherpa-onnx (motor de transcrição no aparelho).
#
# O código nativo lê os objetos de configuração por reflexão JNI (GetFieldID pelo nome de cada campo
# de OnlineRecognizerConfig, OnlineModelConfig, OnlineTransducerModelConfig, FeatureConfig…) e cria
# OnlineRecognizerResult pelo construtor. Do lado Kotlin esses campos só são escritos, e o R8 os
# apagaria: o reconhecedor nasceria com a configuração vazia.
-keep class com.k2fsa.sherpa.onnx.** { *; }
