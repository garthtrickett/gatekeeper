plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
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

        // CRITICAL: Use a custom runner to initialize our Application class in tests
        testInstrumentationRunner = "com.gatekeeper.app.CustomTestRunner"
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

    // Absolutely guarantee Compose doesn't silently downgrade our test libraries
    configurations.all {
        resolutionStrategy {
            force("androidx.test.espresso:espresso-core:3.7.0")
            force("androidx.test.espresso:espresso-idling-resource:3.7.0")
            force("androidx.test.espresso:espresso-intents:3.7.0")
        }
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // THE FIX: Add this line to resolve ViewModelStore and ViewModelStoreOwner
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")

    // Required for ComponentActivity to function correctly in Compose tests
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Glance for OS-level Widgets
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // SQLDelight Database Driver
    implementation("app.cash.sqldelight:android-driver:2.0.2")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Ktor Networking Client
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12") // Proven JVM engine
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // --- Automated Testing Dependencies ---
    // Ktor Mock Client for testing API calls
    androidTestImplementation("io.ktor:ktor-client-mock:2.3.12")

    // Core library for JVM-only unit tests
    testImplementation("junit:junit:4.13.2")

    // Google's fluent assertion library for more readable tests
    testImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("com.google.truth:truth:1.4.2")
    // Core libraries for Android instrumented tests (run on emulator/device)
    // CRITICAL: Aligned test runner and core libraries with Espresso 3.7.0 and Android 16 (API 36)
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")

    // Force all Espresso modules to the latest stable to fix the Android 16 crash
    val espressoVersion = "3.7.0"
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:$espressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-intents:$espressoVersion")

    // SQLDelight in-memory JVM driver for instrumented database tests
    androidTestImplementation("app.cash.sqldelight:sqlite-driver:2.0.2")

    // Jetpack Compose testing
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
