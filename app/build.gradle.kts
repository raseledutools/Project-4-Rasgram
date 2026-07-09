plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compose plugin version Kotlin version (2.1.10) এর সাথে মিল থাকতে হবে
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    id("com.google.gms.google-services") version "4.4.2"
}

android {
    namespace = "com.rasel.rasgram"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rasel.rasgram"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        resourceConfigurations += listOf("en", "bn")
    }

    signingConfigs {
        getByName("debug") {
            // auto debug keystore
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // CI release build uses debug signing (for distribution use proper keystore)
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation")
        checkReleaseBuilds = false
    }
}

dependencies {

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // ─── Core Android ────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // ─── Compose ─────────────────────────────────────────────────────────────
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.6")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")

    // Material Icons - core + extended (extended needed for Message, People, Videocam etc.)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // ─── Image Loading ────────────────────────────────────────────────────────
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil:3.0.4")

    // ─── File Picker ──────────────────────────────────────────────────────────
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ─── CameraX ─────────────────────────────────────────────────────────────
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ─── Firebase ────────────────────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-config-ktx")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // ─── WebRTC ─────────────────────────────────────────────────────────────
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // OkHttp + Google Auth
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")

    // ─── Testing ──────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}