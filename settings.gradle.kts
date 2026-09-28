pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Só o AAR oficial do sherpa-onnx (motor no aparelho), que não está no Maven Central nem no
        // Google Maven. O JitPack serve o mesmo arquivo do release do GitHub; o SHA-256 dele fica
        // preso em gradle/verification-metadata.xml.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.k2-fsa.sherpa-onnx") }
        }
    }
}

rootProject.name = "FlowVoice"

include(":shared")
include(":androidApp")
include(":desktopApp")