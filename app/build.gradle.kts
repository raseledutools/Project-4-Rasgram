plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("com.google.gms.google-services") version "4.4.1"
}

android {
    namespace = "com.tanimul.android_template_kotlin"
    compileSdk = 35 // ✅ Latest SDK

    defaultConfig {
        applicationId = "com.tanimul.android_template_kotlin"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // ✅ আপনার supported ABI শুধু রাখুন (APK size অনেক কমবে)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            // ✅ R8 Full Mode চালু — dead code সম্পূর্ণ বাদ যাবে
            isMinifyEnabled = true
            isShrinkResources = true // ✅ অব্যবহৃত resource বাদ যাবে
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    // ✅ App Bundle চালু করলে Play Store 20MB এর নিচে নামবে
    bundle {
        language {
            enableSplit = true // শুধু ডিভাইসের ভাষা নামবে
        }
        density {
            enableSplit = true // শুধু ডিভাইসের screen density নামবে
        }
        abi {
            enableSplit = true // শুধু ডিভাইসের ABI নামবে
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true // ✅ Java 8+ API support
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all" // ✅ Performance boost
        )
    }

    buildFeatures {
        compose = true
        buildConfig = false // ✅ অপ্রয়োজনীয় BuildConfig বাদ
    }

    composeOptions {
        // Compose compiler version Kotlin version-এর সাথে মিলিয়ে রাখুন
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "DebugProbesKt.bin",       // ✅ Coroutines debug file বাদ
                "kotlin-tooling-metadata.json"
            )
        }
        // ✅ Duplicate native libs এর error এড়াতে
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // ✅ Lint: unused resource ও translation warning বাদ
    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation")
        checkReleaseBuilds = false
    }
}

dependencies {

    // ✅ Java 8+ desugaring (compileOptions-এর সাথে pair)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // ─── Core Android ────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // ─── Compose ─────────────────────────────────────────────────────────────
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.11.00") // ✅ Latest BOM
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")

    // ⚠️ Material Icons Extended ~10MB+ — শুধু দরকারী icons আলাদাভাবে নিন
    // implementation("androidx.compose.material:material-icons-extended") // ❌ REMOVED
    // ✅ বিকল্প: শুধু core icons (অনেক ছোট)
    implementation("androidx.compose.material:material-icons-core")
    // নির্দিষ্ট icon দরকার হলে SVG থেকে নিজে বানান অথবা
    // https://fonts.google.com/icons থেকে XML নামিয়ে res/drawable-এ রাখুন

    // ─── Image Loading ────────────────────────────────────────────────────────
    // ✅ Coil 3.x — আরও fast ও lightweight
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // ─── File Picker ──────────────────────────────────────────────────────────
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ─── CameraX ─────────────────────────────────────────────────────────────
    // ✅ শুধু দরকারী module রাখুন; camera-extensions বাদ দেওয়া হয়েছে
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ─── Firebase ────────────────────────────────────────────────────────────
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // ─── Cloudinary ──────────────────────────────────────────────────────────
    // ⚠️ cloudinary-android ~5MB+। শুধু upload দরকার হলে REST API সরাসরি ব্যবহার করুন।
    // নিচের লাইন শুধু তখনই রাখুন যদি Cloudinary আসলেই ব্যবহার করছেন:
    // implementation("com.cloudinary:cloudinary-android:3.0.2")

    // ─── WebRTC ───────────────────────────────────────────────────────────────
    // ⚠️ WebRTC SDK সবচেয়ে বড় (~15-20MB)।
    // ✅ শুধু call feature এর screen-এ lazy load করুন।
    // অথবা Firebase Realtime Database দিয়ে signaling + Google Meet link দিন।
    // আপাতত রাখা হলো — কিন্তু এটি বাদ দিলে সবচেয়ে বেশি size কমবে।
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // ─── ML Kit ──────────────────────────────────────────────────────────────
    // ✅ দুটোই রাখা হলো, R8 unused code বাদ দেবে
    // কিন্তু যদি একটাই দরকার হয়, অন্যটা মুছুন
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // ─── Testing ──────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
