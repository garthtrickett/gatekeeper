plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("app.cash.sqldelight")
}

android {
    namespace = "com.gatekeeper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gatekeeper.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-MVP"

        // CRITICAL: Tells Android to use the standard test runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

sqldelight {
  databases {
    create("GatekeeperDatabase") {
      packageName.set("com.gatekeeper.app.db")
    }
  }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Jetpack Compose BOM (Bill of Materials) ensures version compatibility
    // Updated to Oct 2024 for Android 15 / API 35 compatibility
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    
    // Required to run Compose outside of an Activity
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // THE FIX: Add this line to resolve ViewModelStore and ViewModelStoreOwner
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")

    // SQLDelight Database Driver
    implementation("app.cash.sqldelight:android-driver:2.0.2")

    // --- Automated Testing Dependencies ---

    // Core library for JVM-only unit tests
    testImplementation("junit:junit:4.13.2")

    // Google's fluent assertion library for more readable tests
    testImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("com.google.truth:truth:1.4.2")
    // Core libraries for Android instrumented tests (run on emulator/device)
    // CRITICAL: Upgraded to alpha versions to fix InputManager crash on Android 16 (API 36)
    androidTestImplementation("androidx.test:core-ktx:1.7.0-alpha02")
    androidTestImplementation("androidx.test:runner:1.7.0-alpha02")
    androidTestImplementation("androidx.test:rules:1.7.0-alpha02")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0-alpha02")

    // Force all Espresso modules to 3.7.0-alpha02 to fix the Android 16 crash
    val espressoVersion = "3.7.0-alpha02"
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:$espressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-intents:$espressoVersion")

    // SQLDelight in-memory JVM driver for instrumented database tests
    // CRITICAL FIX: Compose ui-test-junit4 transitively pulls in Espresso 3.5.1,
    // which crashes on Android 15/16. We must force all Espresso modules to 3.6.1.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")

    // SQLDelight in-memory JVM driver for instrumented database tests
    androidTestImplementation("app.cash.sqldelight:sqlite-driver:2.0.2")

    // Jetpack Compose testing
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
