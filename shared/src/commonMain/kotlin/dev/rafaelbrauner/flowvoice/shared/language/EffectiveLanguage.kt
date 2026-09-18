package dev.rafaelbrauner.flowvoice.shared.language

fun baseLanguage(tag: String): String =
    tag.split('-', '_').firstOrNull().orEmpty().lowercase()

fun canonicalLanguageCode(tag: String): String = when (baseLanguage(tag)) {
    "nb" -> "no"
    "fil" -> "tl"
    else -> baseLanguage(tag)
}

fun effectiveLanguage(
    intent: String,
    supported: List<String>,
    supportsDetection: Boolean
): String {
    if (supported.isEmpty()) return intent
    if (intent == "zh-Hans" || intent == "zh-Hant") {
        val match = supported.firstOrNull { baseLanguage(it) == baseLanguage(intent) }
        if (match != null) return intent
    }
    if (intent != "auto") {
        val exactBase = supported.firstOrNull { baseLanguage(it) == baseLanguage(intent) }
        if (exactBase != null) return exactBase
        val equivalent = supported.firstOrNull {
            canonicalLanguageCode(it) == canonicalLanguageCode(intent)
        }
        if (equivalent != null) return equivalent
    }
    if (supportsDetection) return "auto"
    val english = supported.firstOrNull { baseLanguage(it) == "en" }
    if (english != null) return english
    val portuguese = supported.firstOrNull { baseLanguage(it) == "pt" }
    if (portuguese != null) return portuguese
    return supported.first()
}
