package com.aegisgatekeeper.app.domain

import java.time.LocalTime

/**
 * Pure function to determine if the Vault is currently unlocked.
 * Hardcoded Check Window: 18:00 (6:00 PM) to 18:30 (6:30 PM).
 */
fun isDeepWorkHours(
    currentTime: LocalTime,
    startMinutes: Int,
    endMinutes: Int,
): Boolean {
    val current = currentTime.hour * 60 + currentTime.minute
    return if (startMinutes <= endMinutes) {
        current in startMinutes until endMinutes
    } else {
        current >= startMinutes || current < endMinutes
    }
}

fun formatMinutesToAmPm(minutes: Int): String {
    var h = minutes / 60
    val m = minutes % 60
    val ampm = if (h >= 12) "PM" else "AM"
    h %= 12
    if (h == 0) h = 12
    return String.format("%d:%02d %s", h, m, ampm)
}

fun parseItunesDuration(duration: String): Long {
    if (duration.isBlank()) return 0L
    if (duration.contains("H") || duration.contains("M")) return parseIso8601Duration(duration)
    val parts = duration.split(":")
    return when (parts.size) {
        3 -> (parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
        2 -> (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
        1 -> parts[0].toLongOrNull() ?: 0L
        else -> 0L
    }
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

fun getNextDeliveryTime(
    currentMinutes: Int,
    currentDay: DayOfWeek,
    rule: BlockingRule.CheckIn,
): Int? {
    if (!rule.daysOfWeek.contains(currentDay)) return null
    return rule.checkInTimesMinutes.sorted().firstOrNull { it > currentMinutes }
}

fun isMailDelivered(
    notificationTimestamp: Long,
    currentTimeMillis: Long,
    rule: BlockingRule.CheckIn,
    currentDay: DayOfWeek,
): Boolean {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = currentTimeMillis
    val currentDayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val currentYear = cal.get(java.util.Calendar.YEAR)
    val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

    cal.timeInMillis = notificationTimestamp
    val notifDayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val notifYear = cal.get(java.util.Calendar.YEAR)
    val notifMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

    if (currentYear > notifYear || currentDayOfYear > notifDayOfYear) {
        return true
    }

    if (!rule.daysOfWeek.contains(currentDay)) return false

    return rule.checkInTimesMinutes.any { checkInTime ->
        checkInTime >= notifMinutes && checkInTime <= currentMinutes
    }
}

fun isVaultUnlocked(
    currentTime: LocalTime,
    startMinutes: Int,
    endMinutes: Int,
): Boolean {
    val current = currentTime.hour * 60 + currentTime.minute
    return if (startMinutes <= endMinutes) {
        current in startMinutes until endMinutes
    } else {
        current >= startMinutes || current < endMinutes
    }
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

fun parseHumanReadableDuration(duration: String): Long {
    if (duration.isBlank()) return 0L
    val parts = duration.split(":")
    return when (parts.size) {
        3 -> (parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
        2 -> (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
        1 -> parts[0].toLongOrNull() ?: 0L
        else -> 0L
    }
}

actual fun parseRssPubDate(
    dateStr: String?,
    fallback: Long,
): Long {
    if (dateStr.isNullOrBlank()) return fallback
    val cleanDate = dateStr.trim().replace(Regex("\\s+"), " ")

    try {
        return java.time.Instant
            .parse(cleanDate)
            .toEpochMilli()
    } catch (e: Exception) {
    }

    val zonedFormatters =
        listOf(
            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
            java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME,
            java.time.format.DateTimeFormatter
                .ofPattern("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("d MMM yyyy HH:mm:ss Z", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("dd MMM yyyy HH:mm:ss Z", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ssX", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", java.util.Locale.US),
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.US),
        )

    for (formatter in zonedFormatters) {
        try {
            return java.time.ZonedDateTime
                .parse(cleanDate, formatter)
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            // Ignore
        }
    }

    val localFormatters =
        listOf(
            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.US),
        )
    for (formatter in localFormatters) {
        try {
            return java.time.LocalDateTime
                .parse(cleanDate, formatter)
                .atZone(java.time.ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
        }
    }

    try {
        return java.time.LocalDate
            .parse(cleanDate, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(java.time.ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        // Ignore
    }

    return fallback
}
