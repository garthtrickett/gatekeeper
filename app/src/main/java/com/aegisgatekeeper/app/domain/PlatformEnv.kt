package com.aegisgatekeeper.app.domain

import com.aegisgatekeeper.app.App

actual fun isDevEnvironment(): Boolean {
    return try {
        App.instance.packageName.endsWith(".dev")
    } catch (e: Exception) {
        false
    }
}
