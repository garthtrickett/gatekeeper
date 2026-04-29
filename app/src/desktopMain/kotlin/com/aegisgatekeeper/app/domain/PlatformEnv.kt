package com.aegisgatekeeper.app.domain

actual fun isDevEnvironment(): Boolean {
    return System.getenv("DEV_MODE") == "true" || System.getenv("GATEKEEPER_DEV_TOKEN") != null
}
