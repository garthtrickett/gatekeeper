package com.aegisgatekeeper.app.domain

import java.time.LocalTime

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

fun getNextDeliveryTime(
    currentMinutes: Int,
    currentDay: DayOfWeek,
    rule: BlockingRule.CheckIn,
): Int? = null

fun isMailDelivered(
    notificationTimestamp: Long,
    currentTimeMillis: Long,
    rule: BlockingRule.CheckIn,
    currentDay: DayOfWeek,
): Boolean = true

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

fun formatMinutesToAmPm(minutes: Int): String {
    var h = minutes / 60
    val m = minutes % 60
    val ampm = if (h >= 12) "PM" else "AM"
    h %= 12
    if (h == 0) h = 12
    return String.format("%d:%02d %s", h, m, ampm)
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
