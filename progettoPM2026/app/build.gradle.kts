import java.util.Properties
import java.io.FileInputStream
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}
android {
    namespace = "com.example.progettopm2026"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.progettopm2026"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val apiKey = localProperties.getProperty("anthropic.api.key") ?: ""
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$apiKey\"")
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
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
    // viewModelScope lives here — extension property on ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    // repeatOnLifecycle and lifecycleScope live here
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // by viewModels() delegate in Activity
    implementation("androidx.activity:activity-ktx:1.9.3")
}