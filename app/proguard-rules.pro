# =================== Firebase ===================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# =================== Kotlin Coroutines ===================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# =================== WebRTC ===================
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# =================== OkHttp ===================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# =================== Coil (Image Loading) ===================
-keep class coil3.** { *; }
-dontwarn coil3.**

# =================== Compose ===================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# =================== Google Auth ===================
-keep class com.google.auth.** { *; }
-dontwarn com.google.auth.**

# =================== Keep Data Classes ===================
-keep class com.rasel.rasgram.** { *; }

# =================== General ===================
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-dontwarn java.lang.invoke.**
-dontwarn **$$serializer