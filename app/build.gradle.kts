plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        getByName("release") {
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Фоновые задачи WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha01")
    
    // Сеть
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
