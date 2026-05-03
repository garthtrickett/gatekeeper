package com.aegisgatekeeper.app.domain

import com.aegisgatekeeper.app.App

actual fun isDevEnvironment(): Boolean =
    try {
        App.instance.packageName.endsWith(".dev")
        } catch (e: Exception) {
        false
    }

actual fun randomUUIDString(): String = java.util.UUID.randomUUID().toString()
actual fun currentTimeMillis(): Long = System.currentTimeMillis()
