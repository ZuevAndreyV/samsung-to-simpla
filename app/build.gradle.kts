plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sync"
        minSdk = 26 // Health Connect требует API 26+
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Health Connect Client
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // WorkManager (для фоновой синхронизации)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // OkHttp для сетевых запросов
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines для асинхронности в MainActivity
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
