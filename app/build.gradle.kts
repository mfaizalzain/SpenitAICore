plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.fmz.spenitaicore"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fmz.spenitaicore"
        minSdk = 31
        targetSdk = 36
        versionCode = 27
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("releaseUpload") {
            val keystorePath = providers.environmentVariable("SPENIT_UPLOAD_KEYSTORE").orNull
            val keystorePassword = providers.environmentVariable("SPENIT_UPLOAD_STORE_PASSWORD").orNull
            val keyAliasValue = providers.environmentVariable("SPENIT_UPLOAD_KEY_ALIAS").orNull
            val keyPasswordValue = providers.environmentVariable("SPENIT_UPLOAD_KEY_PASSWORD").orNull

            if (!keystorePath.isNullOrBlank() &&
                !keystorePassword.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() &&
                !keyPasswordValue.isNullOrBlank()
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val hasReleaseSigning = providers.environmentVariable("SPENIT_UPLOAD_KEYSTORE").isPresent &&
                providers.environmentVariable("SPENIT_UPLOAD_STORE_PASSWORD").isPresent &&
                providers.environmentVariable("SPENIT_UPLOAD_KEY_ALIAS").isPresent &&
                providers.environmentVariable("SPENIT_UPLOAD_KEY_PASSWORD").isPresent
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseUpload")
            }
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    // Core Android
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // CameraX
    val cameraxVersion = "1.6.1"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // Credential Manager (Google Sign-In)
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Sign-In with Drive scope (for backup)
    implementation("com.google.android.gms:play-services-auth:21.6.0")

    // Google AdMob
    implementation("com.google.android.gms:play-services-ads:25.4.0")

    // WorkManager (nightly backup scheduling)
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Home-screen widget (Glance)
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // Biometrics
    implementation("androidx.biometric:biometric:1.1.0")

    // ML Kit (text recognition as fallback when AICore is unavailable)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // ML Kit — Document Scanner (receipt auto-crop)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")

    // AICore (Google's on-device AI with Gemini Nano via ML Kit)
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")

    // OkHttp for remote AI API calls
    implementation("com.squareup.okhttp3:okhttp:5.4.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
}
