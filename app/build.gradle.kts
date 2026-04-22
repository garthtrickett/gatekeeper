import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.aegisgatekeeper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aegisgatekeeper.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0-MVP"

        // CRITICAL: Use a custom runner to initialize our Application class in tests
        testInstrumentationRunner = "com.aegisgatekeeper.app.CustomTestRunner"

        // Read the API Key from local.properties
        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        }

        val youtubeKey = properties.getProperty("YOUTUBE_API_KEY") ?: "REPLACE_WITH_YOUR_YOUTUBE_API_KEY"
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res")
        }
    }
}

sqldelight {
  databases {
    create("GatekeeperDatabase") {
      packageName.set("com.aegisgatekeeper.app.db")
      srcDirs.setFrom("src/main/sqldelight")
      verifyMigrations.set(true)
    }
  }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
    
    jvm {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":common-shared"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-auth:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
                implementation("app.cash.sqldelight:primitive-adapters:2.0.2")
                implementation("media.kamel:kamel-image:0.9.4")
                implementation("io.arrow-kt:arrow-core:1.2.0")
            }
        }
        
        val androidMain by getting {
            // Map existing Android source directory to KMP androidMain temporarily during migration
            kotlin.srcDirs("src/main/java", "src/main/kotlin")
            
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
                implementation("androidx.savedstate:savedstate-ktx:1.2.1")
                implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.glance:glance-appwidget:1.1.0")
                implementation("androidx.glance:glance-material3:1.1.0")
                implementation("androidx.security:security-crypto:1.0.0")
                implementation("app.cash.sqldelight:android-driver:2.0.2")
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
                implementation("androidx.work:work-runtime-ktx:2.9.0")
                implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
                implementation("com.google.firebase:firebase-analytics")
                implementation("com.google.firebase:firebase-messaging-ktx")
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                // Core test dependencies for JUnit and Coroutines
                implementation("junit:junit:4.13.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

                // AndroidX Compose UI Testing dependencies
                val composeTestVersion = "1.6.8"
                implementation("androidx.compose.ui:ui-test-junit4:$composeTestVersion")

                implementation("io.ktor:ktor-client-mock:2.3.12")
                implementation("com.google.truth:truth:1.4.2")
                implementation("androidx.test:core-ktx:1.6.1")
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test:rules:1.6.1")
                implementation("androidx.test.ext:junit-ktx:1.2.1")
                val espressoVersion = "3.7.0"
                implementation("androidx.test.espresso:espresso-core:$espressoVersion")
                implementation("androidx.test.espresso:espresso-idling-resource:$espressoVersion")
                implementation("androidx.test.espresso:espresso-intents:$espressoVersion")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("com.google.truth:truth:1.4.2")
            }
        }
        
        val jvmMain by getting {
            kotlin.srcDirs("src/desktopMain/kotlin")
            resources.srcDirs("src/desktopMain/resources")
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
                implementation("io.ktor:ktor-client-cio:2.3.12")
                implementation("org.slf4j:slf4j-simple:2.0.9")
                
                // JCEF for Desktop Web Filtering
                implementation("dev.datlag:kcef:2024.01.07.1")
            }
        }
    }
}

tasks.withType<JavaExec> {
    val ldLibraryPath = System.getenv("LD_LIBRARY_PATH")
    if (ldLibraryPath != null) {
        environment("LD_LIBRARY_PATH", ldLibraryPath)
        systemProperty("java.library.path", ldLibraryPath)
    }
}

compose.desktop {
    application {
        mainClass = "com.aegisgatekeeper.app.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "gatekeeper"
            packageVersion = "1.0.0"
        }
        buildTypes.release {
            proguard {
                isEnabled.set(false)
            }
        }
    }
}

dependencies {
    "debugImplementation"("androidx.compose.ui:ui-test-manifest:1.6.8")
}
