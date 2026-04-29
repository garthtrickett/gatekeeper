package com.aegisgatekeeper.app.domain

actual fun isDevEnvironment(): Boolean = System.getenv("DEV_MODE") == "true" || System.getenv("GATEKEEPER_DEV_TOKEN") != null
