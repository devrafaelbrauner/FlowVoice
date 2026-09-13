import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvm("desktop")
    jvmToolchain(17)

    sourceSets {
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.rafaelbrauner.flowvoice.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "FlowVoice"
            packageVersion = "0.3.0"
        }
    }
}
