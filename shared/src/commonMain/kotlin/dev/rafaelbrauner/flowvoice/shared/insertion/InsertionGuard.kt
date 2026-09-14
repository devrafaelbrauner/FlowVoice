package dev.rafaelbrauner.flowvoice.shared.insertion

object InsertionGuard {
    const val PASSWORD_MESSAGE = "campo de senha: nada inserido"
    const val DESTINATION_CHANGED_MESSAGE = "o foco mudou de app; texto mantido na barra"

    private const val TYPE_MASK_CLASS = 0x0000000f
    private const val TYPE_MASK_VARIATION = 0x00000ff0
    private const val TYPE_CLASS_TEXT = 0x00000001
    private const val TYPE_CLASS_NUMBER = 0x00000002
    private const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    private const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    private const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    private const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010

    fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and TYPE_MASK_VARIATION
        return when (inputType and TYPE_MASK_CLASS) {
            TYPE_CLASS_TEXT -> variation == TYPE_TEXT_VARIATION_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_WEB_PASSWORD
            TYPE_CLASS_NUMBER -> variation == TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    fun destinationChanged(startPackage: String?, currentPackage: String?): Boolean =
        startPackage != null && currentPackage != null && startPackage != currentPackage
}
