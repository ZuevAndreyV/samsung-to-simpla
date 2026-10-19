plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sync"
        minSdk = 26 // Поддержка Android 8.0 и выше
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Базовые библиотеки AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // Жизненный цикл (lifecycleScope для MainActivity)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Фоновый робот WorkManager (SyncDataWorker)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Системный клиент Health Connect от Google (GPS, пульс, сессии)
    implementation("androidx.health.connect:health-connect-client:1.1.0-alpha07")

    // Сетевой клиент OkHttp3 для отправки JSON на бэкенд Simple Run
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
