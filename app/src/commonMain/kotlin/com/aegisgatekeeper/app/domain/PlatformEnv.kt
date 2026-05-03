package com.aegisgatekeeper.app.domain

expect fun isDevEnvironment(): Boolean
expect fun randomUUIDString(): String
expect fun currentTimeMillis(): Long
