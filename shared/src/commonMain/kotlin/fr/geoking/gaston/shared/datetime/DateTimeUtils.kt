package fr.geoking.gaston.shared.datetime

import fr.geoking.gaston.shared.platform.getSystemLanguage
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

object DateTimeUtils {

    fun parseFlexible(dateStr: String): Instant? {
        // Attempt ISO format first: 2024-05-20T10:20:30Z
        try {
            return Instant.parse(dateStr)
        } catch (_: Exception) {
        }

        // Attempt ODS format (DataGouv): 2024-05-20T10:20:30+02:00
        // (Instant.parse handles ISO-8601 with offset in recent kotlinx-datetime versions)

        // Attempt "YYYY-MM-DD HH:MM:SS" (Mimit)
        try {
            val space = dateStr.indexOf(' ')
            if (space == 10) {
                val iso = dateStr.replace(' ', 'T') + "Z" // Assume UTC if no zone
                return Instant.parse(iso)
            }
        } catch (_: Exception) {
        }

        // Attempt YYYY-MM-DD
        try {
            if (dateStr.length == 10 && dateStr[4] == '-' && dateStr[7] == '-') {
                return (dateStr + "T00:00:00Z").let { Instant.parse(it) }
            }
        } catch (_: Exception) {
        }

        return null
    }

    fun formatRelativeTime(dateStr: String): String {
        val instant = parseFlexible(dateStr) ?: return dateStr
        val now = kotlin.time.Clock.System.now()
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
            else -> {
                val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${localDateTime.day}/${localDateTime.month}/${localDateTime.year}"
            }
        }
    }
}
