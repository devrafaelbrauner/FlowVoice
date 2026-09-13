package dev.rafaelbrauner.flowvoice.shared.transcription

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt

class DpapiSecretCipher : SecretCipher {
    override fun protect(plainText: ByteArray): ByteArray =
        Crypt32Util.cryptProtectData(
            plainText,
            entropy(),
            WinCrypt.CRYPTPROTECT_UI_FORBIDDEN,
            DESCRIPTION,
            null
        )

    override fun unprotect(cipherText: ByteArray): ByteArray =
        Crypt32Util.cryptUnprotectData(
            cipherText,
            entropy(),
            WinCrypt.CRYPTPROTECT_UI_FORBIDDEN,
            null
        )

    private fun entropy(): ByteArray = ENTROPY.toByteArray(Charsets.UTF_8)

    private companion object {
        private const val ENTROPY = "dev.rafaelbrauner.flowvoice/openrouter-key"
        private const val DESCRIPTION = "FlowVoice OpenRouter key"
    }
}
