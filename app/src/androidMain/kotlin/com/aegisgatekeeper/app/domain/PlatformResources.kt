package com.aegisgatekeeper.app.domain

import com.aegisgatekeeper.app.App

actual fun readResource(path: String): String? =
    try {
        App.instance.assets
            .open(path)
            .bufferedReader()
            .use { it.readText() }
    } catch (e: Exception) {
        platformLog("Gatekeeper", "Failed to read asset: $path, Error: ${e.message}")
        null
    }
