package fr.geoking.gaston.shared.datetime

import fr.geoking.gaston.shared.platform.getSystemLanguage
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

object DateTimeUtils {

    fun parseFlexible(dateStr: String): Instant? {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return null

        // Attempt ISO format first: 2024-05-20T10:20:30Z
        try {
            return Instant.parse(trimmed)
        } catch (_: Exception) {
        }

        // Attempt ODS format (DataGouv): 2024-05-20T10:20:30+02:00
        // (Instant.parse handles ISO-8601 with offset in recent kotlinx-datetime versions)

        // Attempt "YYYY-MM-DD HH:MM:SS" (GasAPI / Mimit) — optional fractional seconds
        try {
            val space = trimmed.indexOf(' ')
            if (space == 10 && trimmed.length >= 19) {
                val datePart = trimmed.substring(0, 10)
                val timePart = trimmed.substring(11).take(8) // HH:MM:SS
                if (timePart.length == 8 && timePart[2] == ':' && timePart[5] == ':') {
                    return Instant.parse("${datePart}T${timePart}Z")
                }
            }
        } catch (_: Exception) {
        }

        // Attempt YYYY-MM-DD
        try {
            if (trimmed.length == 10 && trimmed[4] == '-' && trimmed[7] == '-') {
                return Instant.parse("${trimmed}T00:00:00Z")
            }
        } catch (_: Exception) {
        }

        return null
    }

    /**
     * Parse [dateStr] and return a canonical ISO-8601 UTC string (e.g. `2026-10-06T00:01:00Z`),
     * or null if unparseable.
     */
    fun normalizeToIso(dateStr: String): String? =
        parseFlexible(dateStr)?.toString()

    /**
     * True when [dateStr] parses and is strictly older than [days] full days before [now].
     * Unparseable / blank strings are not considered old.
     */
    fun isOlderThanDays(
        dateStr: String,
        days: Int,
        now: Instant = Clock.System.now()
    ): Boolean {
        val instant = parseFlexible(dateStr) ?: return false
        return (now - instant).inWholeDays > days
    }

    /**
     * Format [dateStr] as a calendar date `dd/MM/yyyy` (e.g. `25/11/2026`).
     * Unparseable / blank strings are returned trimmed (or as-is if empty after trim).
     */
    fun formatDate(dateStr: String): String {
        val instant = parseFlexible(dateStr) ?: return dateStr.trim().ifEmpty { dateStr }
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val day = local.day.toString().padStart(2, '0')
        val month = local.month.number.toString().padStart(2, '0')
        return "$day/$month/${local.year}"
    }

    fun formatRelativeTime(dateStr: String): String {
        val instant = parseFlexible(dateStr) ?: return dateStr.trim().ifEmpty { dateStr }
        val now = Clock.System.now()
        val duration = now - instant

        val seconds = duration.inWholeSeconds
        val minutes = duration.inWholeMinutes
        val hours = duration.inWholeHours
        val days = duration.inWholeDays

        val isFr = getSystemLanguage() == "fr"

        return when {
            seconds < 60 -> if (isFr) "à l'instant" else "just now"
            minutes < 60 -> if (isFr) "il y a ${minutes} min" else "${minutes}min ago"
            hours < 24 -> {
                val remainingMinutes = minutes % 60
                val hStr = if (isFr) {
                    if (hours == 1L) "heure" else "heures"
                } else {
                    if (hours == 1L) "hour" else "hours"
                }
                if (isFr) {
                    if (remainingMinutes > 0) "il y a $hours $hStr et ${remainingMinutes} min"
                    else "il y a $hours $hStr"
                } else {
                    if (remainingMinutes > 0) "$hours $hStr ${remainingMinutes}min ago"
                    else "$hours $hStr ago"
                }
            }
            days < 7 -> {
                val dStr = if (isFr) {
                    if (days == 1L) "jour" else "jours"
                } else {
                    if (days == 1L) "day" else "days"
                }
                if (isFr) "il y a $days $dStr"
                else "$days $dStr ago"
            }
            days < 30 -> {
                val weeks = days / 7
                if (isFr) {
                    if (weeks == 1L) "il y a 1 semaine" else "il y a $weeks semaines"
                } else {
                    if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
                }
            }
            else -> formatDate(dateStr)
        }
    }
}
