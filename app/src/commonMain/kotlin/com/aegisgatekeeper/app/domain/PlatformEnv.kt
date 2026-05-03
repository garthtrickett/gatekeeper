package com.aegisgatekeeper.app.domain

expect fun isDevEnvironment(): Boolean
expect fun randomUUIDString(): String
expect fun currentTimeMillis(): Long
expect fun platformLog(tag: String, message: String)
expect fun parseRssPubDate(dateStr: String?, fallback: Long): Long
