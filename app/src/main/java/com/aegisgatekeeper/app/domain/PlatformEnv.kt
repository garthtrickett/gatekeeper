package com.aegisgatekeeper.app.domain

import com.aegisgatekeeper.app.App

actual fun isDevEnvironment(): Boolean =
    try {
        App.instance.packageName.endsWith(".dev")
    } catch (e: Exception) {
        false
    }

actual fun randomUUIDString(): String =
    java.util.UUID
        .randomUUID()
        .toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun platformLog(
    tag: String,
    message: String,
) {
    android.util.Log.d(tag, message)
}

actual fun computeHash(input: String): String {
    val bytes =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(input.encodeToByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
