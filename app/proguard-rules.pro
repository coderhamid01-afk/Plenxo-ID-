# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# 1. Protect All App Data Models (Crucial for Firestore & JSON Parsing)
-keep class com.coderhamid.plenxo.model.** { *; }
-keep class com.coderhamid.plenxo.data.** { *; }
-keep class com.example.model.** { *; }
-keep class com.example.network.** { *; }
-keep class com.app.domain.** { *; }
-keep class com.plenxo.app.models.** { *; }
-keepclassmembers class com.plenxo.app.models.** { *; }
-keepclassmembers class com.example.model.** { *; }
-keepclassmembers class ** { @com.google.gson.annotations.SerializedName <fields>; }
-keepclassmembers class ** { @com.squareup.moshi.** <fields>; }

# 2. Protect Firebase & Firestore (To prevent runtime crashes during DB read/write)
-keep class com.google.firebase.** { *; }
-keepclassmembers class * { *** get*(); void set*(***); }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 3. Protect Retrofit & OkHttp (Crucial for Brevo OTP & Catbox APIs)
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keepattributes Exceptions

# 4. Protect Android Lifecycle & ViewModels
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Supabase & Ktor rules
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }

# 5. Kotlin Coroutines & Serialization Support
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-dontwarn kotlinx.serialization.**

# 6. WebRTC SDK Classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# 7. Android KeyStore & Cryptographic Helper Models
-keep class com.example.util.EncryptionManager { *; }
-keep class com.example.util.SecurityManager { *; }

# 8. Defensive logging strip for release builds (Strips Log.d, Log.v and System.out.println)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

-assumenosideeffects class java.io.PrintStream {
    public static *** println(...);
}
