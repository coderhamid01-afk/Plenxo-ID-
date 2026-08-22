import java.util.Properties

fun getSafeProperty(key: String, defaultValue: String = ""): String {
    val rawValue = project.findProperty(key)?.toString()?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: defaultValue
    val cleanValue = rawValue.replace("\"", "")
    return "\"$cleanValue\""
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.coderhamid.plenxo.me"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Active Required Fields
    val apiKey = ((project.findProperty("FIREBASE_API_KEY") as? String).takeIf { !it.isNullOrBlank() }
        ?: System.getenv("FIREBASE_API_KEY").takeIf { !it.isNullOrBlank() }
        ?: "AIzaSyCFn6EwrMWdpxNO_f9SweSirWu_gbhVzAI").replace("\"", "").trim()

    val projectId = ((project.findProperty("FIREBASE_PROJECT_ID") as? String).takeIf { !it.isNullOrBlank() }
        ?: System.getenv("FIREBASE_PROJECT_ID").takeIf { !it.isNullOrBlank() }
        ?: "plenxo-afb01").replace("\"", "").trim()

    val appId = ((project.findProperty("FIREBASE_APP_ID") as? String).takeIf { !it.isNullOrBlank() }
        ?: System.getenv("FIREBASE_APP_ID").takeIf { !it.isNullOrBlank() }
        ?: "1:1010184005812:android:b9a9a1649f677273aa8fe1").replace("\"", "").trim()

    val dbUrl = ((project.findProperty("FIREBASE_DATABASE_URL") as? String).takeIf { !it.isNullOrBlank() }
        ?: System.getenv("FIREBASE_DATABASE_URL").takeIf { !it.isNullOrBlank() }
        ?: "https://plenxo-afb01-default-rtdb.firebaseio.com").replace("\"", "").trim()

    val netlifyUrl = ((project.findProperty("NETLIFY_OTP_URL") as? String).takeIf { !it.isNullOrBlank() }
        ?: System.getenv("NETLIFY_OTP_URL").takeIf { !it.isNullOrBlank() }
        ?: "https://plenxo-back.netlify.app/api/send-otp").replace("\"", "").trim()

    val cfUrl = ((project.findProperty("CLOUDFLARE_WORKER_URL") as? String).takeIf { !it.isNullOrBlank() }
        ?: System.getenv("CLOUDFLARE_WORKER_URL").takeIf { !it.isNullOrBlank() }
        ?: "https://plenxo-back.netlify.app/api/send-otp").replace("\"", "").trim()

    buildConfigField("String", "FIREBASE_API_KEY", "\"$apiKey\"")
    buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$projectId\"")
    buildConfigField("String", "FIREBASE_APP_ID", "\"$appId\"")
    buildConfigField("String", "FIREBASE_DATABASE_URL", "\"$dbUrl\"")
    buildConfigField("String", "NETLIFY_OTP_URL", "\"$netlifyUrl\"")
    buildConfigField("String", "CLOUDFLARE_WORKER_URL", "\"$cfUrl\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  lint {
    baseline = file("lint-baseline.xml")
    abortOnError = false
    checkReleaseBuilds = false
    ignoreWarnings = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
    viewBinding = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.appcompat)
  implementation("androidx.browser:browser:1.8.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("com.google.android.material:material:1.12.0")
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.work.runtime)
  implementation(platform(libs.androidx.compose.bom))
  

  // Firebase Auth and Firestore
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
  implementation(libs.androidx.media3.transformer)
  implementation(libs.androidx.media3.effect)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.navigation.compose)
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.process)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.sqlcipher)
  implementation(libs.coil.compose)
  implementation(libs.coil)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  implementation("com.google.zxing:core:3.5.3")
  implementation(libs.webrtc)
  
  // Firebase (Needed until migration is complete)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.database)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.messaging)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.biometric)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
  
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// No printEnv task needed

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
  }
}
