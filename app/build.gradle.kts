plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.fmz.spenitaicore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fmz.spenitaicore"
        minSdk = 31
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.5"

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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    // Core Android
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // CameraX
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Credential Manager (Google Sign-In)
    implementation("androidx.credentials:credentials:1.5.0-rc01")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0-rc01")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Sign-In with Drive scope (for backup)
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Google AdMob
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // WorkManager (nightly backup scheduling)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Biometrics
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // ML Kit (text recognition as fallback when AICore is unavailable)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // AICore (Google's on-device AI with Gemini Nano via ML Kit)
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
