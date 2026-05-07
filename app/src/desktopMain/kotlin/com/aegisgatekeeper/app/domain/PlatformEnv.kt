package com.aegisgatekeeper.app.domain

actual fun isDevEnvironment(): Boolean = System.getenv("DEV_MODE") == "true" || System.getenv("GATEKEEPER_DEV_TOKEN") != null

actual fun randomUUIDString(): String =
    java.util.UUID
        .randomUUID()
        .toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformLog(
    tag: String,
    message: String,
) {
    println("[$tag] $message")
}

actual fun computeHash(input: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.encodeToByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
