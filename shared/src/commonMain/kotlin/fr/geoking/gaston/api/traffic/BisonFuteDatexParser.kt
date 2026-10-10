package fr.geoking.gaston.api.traffic

/**
 * Thin wrapper around [DatexSituationParser] for the open Bison Futé / TIPI DATEX feed.
 */
object BisonFuteDatexParser {

    fun parse(xml: String, nowMs: Long = System.currentTimeMillis()): List<TrafficEvent> =
        DatexSituationParser.parse(xml, nowMs = nowMs, defaultRoadRef = "FR")

    fun parseDatexTime(s: String?): Long? = DatexSituationParser.parseDatexTime(s)
}
