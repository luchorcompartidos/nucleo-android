plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Núcleo genérico: no conoce "recordatorios" ni "mensajes", solo lo que comparten las apps.
android {
    namespace = "com.mis.nucleo"
    compileSdk = 35

    defaultConfig {
        minSdk = 34          // Android 14
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
    }
}
