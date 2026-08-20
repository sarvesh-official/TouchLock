import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Gradle Play Publisher — uploads AAB to Play Console from Gradle
    id("com.github.triplet.play") version "3.12.1"
}

// Load keystore properties from local file (for dev builds)
// In CI, these come from environment variables
val keystoreProperties = Properties().apply {
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        load(FileInputStream(keystorePropsFile))
    }
}

android {
    namespace = "com.sarvesh.touchlock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sarvesh.touchlock"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Local dev: read from keystore.properties (paths relative to android/ root)
            // CI: read from environment variables
            val storeFilePath = System.getenv("KEYSTORE_FILE")
                ?: keystoreProperties.getProperty("storeFile", "")
            storeFile = rootProject.file(storeFilePath)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword", "")
            keyAlias = System.getenv("KEY_ALIAS")
                ?: keystoreProperties.getProperty("keyAlias", "")
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: keystoreProperties.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    bundle {
        // AAB with language splits for smaller downloads
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

// ─── Play Console Publishing ────────────────────────────────────────────────
// Uses a service account JSON key for authentication.
// In CI: set PLAY_SERVICE_ACCOUNT_JSON environment variable (base64-encoded)
// Locally: place service-account.json in android/ directory

play {
    serviceAccountCredentials = file("play-service-account.json")
    track = System.getenv("PLAY_TRACK") ?: "internal"
    defaultToAppBundles = true
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Google Play Billing for Supporter IAP
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    // Google Play In-App Review
    implementation("com.google.android.play:review:2.0.2")
}
