plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI पर version = GitHub build number, ताकि app नया update पहचान सके
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.alok.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alok.jarvis"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
        ndk { abiFilters += listOf("arm64-v8a") }   // S26 Ultra; keeps the APK small
    }

    // एक ही key से sign — key GitHub Secret से आती है (repo में नहीं)
    val ks = file("jarvis.keystore")
    signingConfigs {
        if (ks.exists()) create("jarvis") {
            storeFile = ks
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "jarvisAlok2026"
            keyAlias = "jarvis"
            keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "jarvisAlok2026"
        }
    }

    buildTypes {
        // Release build: not debuggable, so no one can read the app's private data over USB/adb
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (ks.exists()) signingConfig = signingConfigs.getByName("jarvis")
        }
        if (ks.exists()) {
            getByName("debug") { signingConfig = signingConfigs.getByName("jarvis") }
        }
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(files("libs/sherpa-onnx.aar"))   // offline "Jarvis" wake word (downloaded by CI)
    implementation("androidx.core:core:1.13.1")
}
