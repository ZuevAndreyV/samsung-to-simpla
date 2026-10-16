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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.0"))
    implementation("androidx.compose.ui:compose-ui")
    implementation("androidx.compose.ui:compose-ui-graphics")
    implementation("androidx.compose.material3:material3")

    // Стабильная версия WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Согласованная версия Google Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha06")
    
    // Вспомогательные библиотеки
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
