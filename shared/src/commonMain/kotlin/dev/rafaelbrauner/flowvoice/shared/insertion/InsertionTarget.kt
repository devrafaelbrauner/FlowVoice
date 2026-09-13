package dev.rafaelbrauner.flowvoice.shared.insertion

object InsertionTarget {
    const val OWN_APP_MESSAGE = "campo do próprio FlowVoice: texto mantido na prévia"

    fun isOwnApp(targetPackage: CharSequence?, ownPackage: String): Boolean =
        targetPackage?.toString() == ownPackage
}
