package com.aegisgatekeeper.app.domain

import java.time.LocalTime

/**
 * Pure function to determine if the Vault is currently unlocked.
 * Hardcoded Check Window: 18:00 (6:00 PM) to 18:30 (6:30 PM).
 */
fun isDeepWorkHours(currentTime: LocalTime): Boolean {
    val start = LocalTime.of(9, 0)
    val end = LocalTime.of(17, 0)
    return !currentTime.isBefore(start) && currentTime.isBefore(end)
}

fun parseIso8601Duration(duration: String): Long {
    var hours = 0L
    var minutes = 0L
    var seconds = 0L
    val match = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").find(duration)
    if (match != null) {
        hours = match.groupValues[1].takeIf { it.isNotEmpty() }?.toLong() ?: 0L
        minutes = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: 0L
        seconds = match.groupValues[3].takeIf { it.isNotEmpty() }?.toLong() ?: 0L
    }
    return hours * 3600 + minutes * 60 + seconds
}

fun isVaultUnlocked(currentTime: LocalTime): Boolean {
    val start = LocalTime.of(18, 0)
    val end = LocalTime.of(18, 30)
    return !currentTime.isBefore(start) && currentTime.isBefore(end)
}

fun estimateReadTimeSeconds(html: String): Long {
    val scriptRegex = Regex("<script\\b[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
    val styleRegex = Regex("<style\\b[^>]*>([\\s\\S]*?)</style>", RegexOption.IGNORE_CASE)
    val tagRegex = Regex("<[^>]*>")

    val withoutScripts = scriptRegex.replace(html, " ")
    val withoutStyles = styleRegex.replace(withoutScripts, " ")
    val pureText = tagRegex.replace(withoutStyles, " ")

    val wordCount = pureText.split(Regex("\\s+")).count { it.isNotBlank() }
    return (wordCount / 225.0 * 60).toLong()
}
