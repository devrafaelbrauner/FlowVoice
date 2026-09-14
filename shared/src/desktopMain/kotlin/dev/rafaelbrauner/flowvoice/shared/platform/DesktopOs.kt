package dev.rafaelbrauner.flowvoice.shared.platform

enum class DesktopOs {
    Windows,
    MacOs,
    Linux,
    Other;

    companion object {
        fun fromOsName(osName: String?): DesktopOs {
            val name = osName?.trim()?.lowercase().orEmpty()
            return when {
                name.startsWith("windows") -> Windows
                name.startsWith("mac") || name.startsWith("darwin") -> MacOs
                name.startsWith("linux") -> Linux
                else -> Other
            }
        }

        fun current(): DesktopOs = fromOsName(System.getProperty("os.name"))
    }
}
