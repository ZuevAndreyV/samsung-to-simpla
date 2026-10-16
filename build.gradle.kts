plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sync"
    compileSdk = 34 // Сборка под Android 14

    defaultConfig {
        applicationId = "com.example.sync"
        minSdk = 26     // Минимально Android 8.0 (чтобы работал WorkManager)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    buildFeatures {
        compose = true // Включаем Jetpack Compose для интерфейса
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    // Системные библиотеки Android и Интерфейс (Compose)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.0"))
    implementation("androidx.compose.ui:compose-ui")
    implementation("androidx.compose.ui:compose-ui-graphics")
    implementation("androidx.compose.material3:material3")

    // КРИТИЧЕСКИ ВАЖНО: Модуль фоновой работы (WorkManager)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // КРИТИЧЕСКИ ВАЖНО: Модуль здоровья (Google Health Connect)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    
    // Библиотека для отправки интернет-запросов (понадобится для Simpla)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
