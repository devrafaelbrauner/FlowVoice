package dev.rafaelbrauner.flowvoice.shared.localasr

data class ModelFile(val name: String, val bytes: Long)

// O modelo do motor no aparelho: NVIDIA Nemotron 3.5 ASR Streaming 0.6B (blocos de 560 ms, int8),
// no pacote do sherpa-onnx. Baixado do GitHub (k2-fsa/sherpa-onnx) na primeira vez, nunca
// empacotado no APK. Licença do modelo: OpenMDW-1.1. Tamanhos e hash conferidos no pacote de
// 2026-06-11 (o mesmo que o teclado usa no S26).
object NemotronModel {
    const val DIR_NAME = "nemotron-3.5-asr-streaming-0.6b-560ms-int8"
    const val ARCHIVE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
        "sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11.tar.bz2"
    const val ARCHIVE_BYTES = 475_271_763L
    const val ARCHIVE_SHA256 = "c6bf5e0df765f9d5b43bc9e0536d4b4b3e7d40bdf5ecf13e45f134c51c05ae3a"

    // Idioma por fluxo: "pt" neste modelo é pt-PT (prompt 13); "pt-BR" é o 12. Medido no S26 com as
    // frases de trabalho do teclado: 43,4 % de erro com "pt", 39,5 % com "pt-BR".
    const val LANGUAGE = "pt-BR"

    val FILES = listOf(
        ModelFile("encoder.int8.onnx", 657_601_403L),
        ModelFile("decoder.int8.onnx", 14_978_075L),
        ModelFile("joiner.int8.onnx", 9_504_438L),
        ModelFile("tokens.txt", 131_440L)
    )

    val INSTALLED_BYTES: Long = FILES.sumOf { it.bytes }

    // Durante a instalação o pacote (475 MB) e os arquivos extraídos (682 MB) convivem no disco.
    const val FREE_SPACE_MARGIN_BYTES = 64L * 1024 * 1024
    val REQUIRED_FREE_BYTES: Long = ARCHIVE_BYTES + INSTALLED_BYTES + FREE_SPACE_MARGIN_BYTES
}
