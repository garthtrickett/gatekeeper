plugins {
    // These versions match the Android SDK 34/35 defined in our Nix Flake
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
}
