plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alok.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alok.jarvis"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-phase1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Wake word engine ("Jarvis" is a built-in keyword)
    implementation("ai.picovoice:porcupine-android:3.0.3")
}
