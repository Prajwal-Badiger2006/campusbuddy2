import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.campusbuddy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.campusbuddy"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── Load environment variables ──
        // Gemini API key reads from .env first, with local.properties fallback.
        // Firebase-specific vars are read ONLY from .env (google-services.json is the primary source).
        fun loadFromEnv(key: String): String {
            val envFile = rootProject.file(".env")
            return if (envFile.exists()) {
                Properties().apply {
                    envFile.inputStream().use { load(it) }
                }.getProperty(key, "")
            } else ""
        }

        fun loadFromLocalProps(key: String): String {
            val localPropsFile = rootProject.file("local.properties")
            return if (localPropsFile.exists()) {
                Properties().apply {
                    localPropsFile.inputStream().use { load(it) }
                }.getProperty(key, "")
            } else ""
        }

        val geminiApiKey = loadFromEnv("GEMINI_API_KEY").ifEmpty { loadFromLocalProps("gemini.api.key") }
        val firebaseStorageBucket = loadFromEnv("FIREBASE_STORAGE_BUCKET")
        val firebaseProjectId = loadFromEnv("FIREBASE_PROJECT_ID")
        val firebaseApiKey = loadFromEnv("FIREBASE_API_KEY")
        val firebaseAppId = loadFromEnv("FIREBASE_APP_ID")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"$firebaseStorageBucket\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
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
        freeCompilerArgs = listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)

    // Image loading
    implementation(libs.coil.compose)

    // ML Kit Text Recognition (on-device OCR)
    implementation(libs.mlkit.text.recognition)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Guava (needed for ListenableFuture used by CameraX)
    implementation(libs.guava)

    // Accompanist
    implementation(libs.accompanist.systemuicontroller)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
