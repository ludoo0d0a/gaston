package fr.geoking.gaston.api.romania

/**
 * Peco Online (Romania) Parse REST API constants.
 * Credentials: [ROMANIA_PECO_APPLICATION_ID] and [ROMANIA_PECO_CLIENT_KEY] (see docs/ENV_VARS.md).
 */
internal object RomaniaPecoApi {
    const val API_URL =
        "https://pg-app-hnf14cfy2xb2v9x9eueuchcd2xyetd.scalabl.cloud/1/classes/farapret3"

    /** Parse `where` filter: stations with a valid regular gasoline price (999999 = no data). */
    const val WHERE_JSON = """{"Benzina_Regular":{"${'$'}gt":0,"${'$'}lt":999999}}"""

    const val LAT_MIN = 43.5
    const val LAT_MAX = 48.3
    const val LNG_MIN = 20.2
    const val LNG_MAX = 30.0

    const val PAGE_LIMIT = 1000
    const val NO_DATA_SENTINEL = 999999
}
