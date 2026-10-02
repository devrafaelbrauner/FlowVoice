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
        versionCode = 19
        versionName = "0.8.1"
        // O motor no aparelho é nativo (sherpa-onnx, ~27 MB por arquitetura). Só arm64-v8a: é a do S26 e
        // a dos emuladores deste projeto (AVDs arm64 no Mac com Apple Silicon). O x86_64 somaria 30,8 MB
        // ao APK sem ninguém que o use; armeabi-v7a e x86, mais ~50 MB.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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
                // Caminho relativo vale a partir da raiz, onde fica o keystore.properties (N15).
                storeFile = rootProject.file(releaseStoreFile)
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

    packaging {
        jniLibs {
            // O AAR do sherpa-onnx traz também as APIs C e C++ para quem o usa de código nativo. O app
            // usa só a JNI, e `libsherpa-onnx-jni.so` depende apenas de `libonnxruntime.so`.
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
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
    // Extrair o .tar.bz2 do modelo no aparelho, sem shell nem `tar`.
    implementation(libs.commons.compress)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}