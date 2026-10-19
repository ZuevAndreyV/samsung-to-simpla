plugins {
    id("com.android.application") version "8.2.2"
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "com.example.sync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sync"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    // Базовые системные библиотеки Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Фоны и Сбор данных (WorkManager и Health Connect)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha06")
    
    // Сеть
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Авторизация
   implementation("androidx.security:security-crypto:1.1.0-alpha06")

}
