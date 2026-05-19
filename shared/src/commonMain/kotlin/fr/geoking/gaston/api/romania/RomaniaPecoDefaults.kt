package fr.geoking.gaston.api.romania

/**
 * Default Parse credentials from the Peco Online Android app (also used by pyfuelprices).
 * Build may override via [ROMANIA_PECO_APPLICATION_ID] / [ROMANIA_PECO_CLIENT_KEY].
 */
object RomaniaPecoDefaults {
    const val APPLICATION_ID = "YueWcf0orjSz3IQmaT8yBNDTM5POP0mOU6EDyE3U"
    const val CLIENT_KEY = "ctPx9Ahrz9aaXhEvN0oWCzlX8FHX1cv3r7vZwxH8"
    const val WHERE_CLAUSE =
        """{"Benzina_Regular":{"${'$'}gt":0},"DoarGPL":{"${'$'}ne":1},"Retea":{"${'$'}in":["Gazprom","Lukoil","Mol","OMV","Petrom","Rompetrol","Socar","BLKOil","Ozana","RST"]}}"""
}
