import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.rafaelbrauner.flowvoice"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.rafaelbrauner.flowvoice"
        minSdk = 24
        targetSdk = 36
        versionCode = 16
        versionName = "0.6.0"
    }

    // Chave de release fora do repositório: keystore.properties na raiz (ignorado pelo git) ou
    // variáveis FLOWVOICE_* (CI). Sem nenhum dos dois, o release sai sem assinatura.
    val releaseKeystore = rootProject.file("keystore.properties")
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    fun releaseSetting(property: String, env: String): String? =
        releaseKeystore?.getProperty(property) ?: System.getenv(env)?.takeIf { it.isNotBlank() }
    val releaseStoreFile = releaseSetting("storeFile", "FLOWVOICE_KEYSTORE_FILE")

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseSetting("storePassword", "FLOWVOICE_KEYSTORE_PASSWORD")
                keyAlias = releaseSetting("keyAlias", "FLOWVOICE_KEY_ALIAS")
                keyPassword = releaseSetting("keyPassword", "FLOWVOICE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.koin.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.google.id)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}