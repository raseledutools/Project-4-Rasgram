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
            // শুধু মাত্র আধুনিক ফোনগুলোর জন্য বিল্ড করবে, এতে APK সাইজ অনেক কমে যাবে
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // 🔥 PRO TIP: লাইব্রেরিগুলো থেকে অপ্রয়োজনীয় ভাষা ডিলিট করে শুধু ইংরেজি ও বাংলা রাখবে। এতে প্রায় ১-২ MB সাইজ কমবে।
        resourceConfigurations += listOf("en", "bn")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    
    // 🔥 FIX: material-icons-extended রিমুভ করা হয়েছে। এটি ৩-৪ MB সাইজ খায়। 
    // যে আইকনগুলো লাগবে সেগুলো Google Fonts থেকে SVG ডাউনলোড করে res/drawable এ অ্যাড করে নিবেন।
    implementation("androidx.compose.material:material-icons-core")

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
    // 🔥 FIX: Analytics রিমুভ করা হয়েছে পারফরম্যান্স বুস্ট এবং সাইজ কমানোর জন্য
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    
    // Firebase Cloud Messaging (FCM) - নোটিফিকেশনের জন্য আগের ধাপে বলেছিলাম
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // ─── ML Kit ──────────────────────────────────────────────────────────────
    // 🔥 FIX: চ্যাট অ্যাপে Barcode বা Text Recognition দরকার নেই। 
    // এগুলো প্রায় ১০-১৫ MB সাইজ নিয়ে নিচ্ছিল। তাই বাদ দেওয়া হলো।
    // implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // implementation("com.google.mlkit:text-recognition:16.0.1")

    // ─── WebRTC (~15MB) ─────────────────────────
    // ভিডিও বা অডিও কলের জন্য এটি মাস্ট লাগবে, তাই এটি রাখা হলো। 
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    
    // Push Notification এর জন্য OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    // FCM v1 API এর Access Token জেনারেট করার জন্য
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")

    // ─── Testing ──────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
