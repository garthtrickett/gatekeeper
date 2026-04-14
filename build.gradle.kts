plugins {
    // These versions match the Android SDK 34/35 defined in our Nix Flake
    id("com.android.application") version "8.5.2" apply false
    kotlin("android") version "2.1.0" apply false
    kotlin("plugin.compose") version "2.1.0" apply false
    id("app.cash.sqldelight") version "2.0.2" apply false
}
