plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.progettopm2026"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.progettopm2026"
        minSdk = 29
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // OCR (on-device, free)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // PDF text extraction
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // HTML parsing for URLs
    implementation("org.jsoup:jsoup:1.22.1")
    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // HTTP client for LLM API
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}