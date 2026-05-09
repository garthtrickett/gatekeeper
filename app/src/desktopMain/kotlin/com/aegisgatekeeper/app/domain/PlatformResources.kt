package com.aegisgatekeeper.app.domain

actual fun readResource(path: String): String? =
    try {
        Thread
            .currentThread()
            .contextClassLoader
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
    } catch (e: Exception) {
        platformLog("Gatekeeper", "Failed to read resource: $path, Error: ${e.message}")
        null
    }
