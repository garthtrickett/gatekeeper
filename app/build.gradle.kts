plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        // This version is specifically paired with Kotlin 1.9.24
        kotlinCompilerExtensionVersion = "1.5.14" 
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
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
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
}
