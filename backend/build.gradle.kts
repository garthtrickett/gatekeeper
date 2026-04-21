plugins {
    kotlin("jvm")
    application
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
}

group = "com.aegisgatekeeper.app"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.aegisgatekeeper.backend.ApplicationKt")
}

sqldelight {
    databases {
        create("GatekeeperServerDatabase") {
            packageName.set("com.aegisgatekeeper.backend.db")
            dialect("app.cash.sqldelight:postgresql-dialect:2.0.2")
            srcDirs.setFrom("src/main/sqldelight")
        }
    }
}

dependencies {
    implementation(project(":common-shared"))

    // Ktor
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    // Database (SQLDelight & Postgres)
    implementation("app.cash.sqldelight:jdbc-driver:2.0.2")
    implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // Auth
    implementation("com.auth0:java-jwt:4.4.0")

    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:9.2.0")

    // Email (stubbed)
    implementation("com.sendgrid:sendgrid-java:4.10.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    testImplementation(kotlin("test"))
}

configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

tasks.test {
    useJUnitPlatform()
}
