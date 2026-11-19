package fr.geoking.gaston.api.gas

import fr.geoking.gaston.api.routex.PoiAmenities
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps gas-api.ovh station fields (`services`, `schedules`, `allDayService`, `freeway`)
 * into [PoiAmenities] for station detail UI.
 */
object GasApiAmenities {

    /**
     * True when amenities are missing or have neither hours nor any known service flag.
     * Used to decide whether to run on-demand Overpass enrich.
     */
    fun isThin(amenities: PoiAmenities?): Boolean {
        if (amenities == null) return true
        val hasHours = amenities.openingHoursFuel.isNotEmpty() ||
            amenities.open24h == true ||
            amenities.monOpenFuel != null ||
            amenities.tueOpenFuel != null ||
            amenities.wedOpenFuel != null ||
            amenities.thuOpenFuel != null ||
            amenities.friOpenFuel != null ||
            amenities.satOpenFuel != null ||
            amenities.sunOpenFuel != null
        val hasService = listOf(
            amenities.manned24h,
            amenities.mannedAutomat24h,
            amenities.automat,
            amenities.motorwayIndicator,
            amenities.restaurant,
            amenities.shop,
            amenities.snackbar,
            amenities.carWash,
            amenities.showers,
            amenities.adBluePump,
            amenities.adBlueCanister,
            amenities.toilets,
            amenities.drinkingWater,
            amenities.food,
            amenities.wifi,
            amenities.atm,
            amenities.playground,
            amenities.truckIndicator,
            amenities.truckParking,
            amenities.truckDiesel,
            amenities.truckLane,
            amenities.lpg,
            amenities.cng,
            amenities.lng,
            amenities.dieselBio,
            amenities.hvo100,
        ).any { it == true }
        return !hasHours && !hasService
    }

    fun fromStationJson(obj: JsonObject): PoiAmenities {
        val allDay = obj.booleanField("allDayService")
            ?: obj.booleanField("all_day_service")
        val freeway = obj.booleanField("freeway")
            ?: obj.booleanField("motorway")

        val serviceNames = parseServiceNames(obj["services"])
        val mapped = mapServiceNames(serviceNames)
        val unmapped = serviceNames.filter { name ->
            !isMappedServiceName(name)
        }

        val scheduleHours = parseSchedules(obj["schedules"])
        val openingLines = buildList {
            addAll(scheduleHours.lines)
            if (unmapped.isNotEmpty()) {
                add("Autres services: ${unmapped.joinToString(", ")}")
            }
        }

        return PoiAmenities(
            manned24h = when {
                allDay == true && mapped.automat != true -> true
                else -> mapped.manned24h
            },
            mannedAutomat24h = mapped.mannedAutomat24h,
            automat = mapped.automat,
            motorwayIndicator = freeway ?: mapped.motorwayIndicator,
            restaurant = mapped.restaurant,
            shop = mapped.shop,
            snackbar = mapped.snackbar,
            carWash = mapped.carWash,
            showers = mapped.showers,
            adBluePump = mapped.adBluePump,
            adBlueCanister = mapped.adBlueCanister,
            toilets = mapped.toilets,
            drinkingWater = mapped.drinkingWater,
            food = mapped.food,
            wifi = mapped.wifi,
            atm = mapped.atm,
            playground = mapped.playground,
            truckIndicator = mapped.truckIndicator,
            truckParking = mapped.truckParking,
            truckDiesel = mapped.truckDiesel,
            truckLane = mapped.truckLane,
            lpg = mapped.lpg,
            cng = mapped.cng,
            lng = mapped.lng,
            dieselBio = mapped.dieselBio,
            hvo100 = mapped.hvo100,
            open24h = allDay == true || mapped.open24h == true,
            monOpenFuel = scheduleHours.monOpen,
            monCloseFuel = scheduleHours.monClose,
            tueOpenFuel = scheduleHours.tueOpen,
            tueCloseFuel = scheduleHours.tueClose,
            wedOpenFuel = scheduleHours.wedOpen,
            wedCloseFuel = scheduleHours.wedClose,
            thuOpenFuel = scheduleHours.thuOpen,
            thuCloseFuel = scheduleHours.thuClose,
            friOpenFuel = scheduleHours.friOpen,
            friCloseFuel = scheduleHours.friClose,
            satOpenFuel = scheduleHours.satOpen,
            satCloseFuel = scheduleHours.satClose,
            sunOpenFuel = scheduleHours.sunOpen,
            sunCloseFuel = scheduleHours.sunClose,
            openingHoursFuel = openingLines,
        )
    }

    internal fun mapServiceNames(names: List<String>): PoiAmenities {
        var automat: Boolean? = null
        var shop: Boolean? = null
        var carWash: Boolean? = null
        var toilets: Boolean? = null
        var restaurant: Boolean? = null
        var food: Boolean? = null
        var snackbar: Boolean? = null
        var wifi: Boolean? = null
        var atm: Boolean? = null
        var adBluePump: Boolean? = null
        var adBlueCanister: Boolean? = null
        var truckLane: Boolean? = null
        var truckIndicator: Boolean? = null
        var truckParking: Boolean? = null
        var truckDiesel: Boolean? = null
        var lpg: Boolean? = null
        var showers: Boolean? = null
        var playground: Boolean? = null
        var drinkingWater: Boolean? = null
        var open24h: Boolean? = null
        var mannedAutomat24h: Boolean? = null

        for (raw in names) {
            val n = raw.lowercase()
            when {
                n.contains("automate") && (n.contains("cb") || n.contains("24")) -> {
                    automat = true
                    mannedAutomat24h = true
                    open24h = true
                }
                n.contains("automate") -> automat = true
                n.contains("lavage") -> carWash = true
                n.contains("toilette") -> toilets = true
                n.contains("boutique") || n.contains("shop") || n.contains("alimentaire") -> shop = true
                n.contains("restauration à emporter") || n.contains("emporter") -> {
                    food = true
                    snackbar = true
                }
                n.contains("restauration") || n.contains("restaurant") -> {
                    restaurant = true
                    food = true
                }
                n.contains("wifi") || n.contains("wi-fi") -> wifi = true
                n.contains("dab") || n.contains("distributeur") && n.contains("billet") -> atm = true
                n.contains("adblue") && (n.contains("bidon") || n.contains("canister")) ->
                    adBlueCanister = true
                n.contains("adblue") -> adBluePump = true
                n.contains("poids lourds") || n.contains("piste pl") -> {
                    truckLane = true
                    truckIndicator = true
                }
                n.contains("parking pl") || n.contains("parking poids") -> truckParking = true
                n.contains("gazole") && n.contains("poids") -> truckDiesel = true
                n.contains("gpl") || n.contains("gplc") || n.contains("gaz domestique") || n.contains("butane") || n.contains("propane") ->
                    lpg = true
                n.contains("douche") -> showers = true
                n.contains("aire de jeux") || n.contains("playground") -> playground = true
                n.contains("eau potable") || n.contains("drinking") -> drinkingWater = true
                n.contains("gonflage") -> { /* shown via unmapped / leftover line if needed */ }
            }
            // Gonflage has no dedicated PoiAmenities flag — left for "Autres services" when unmapped.
            // Mark shop for boutique non alimentaire already covered by "boutique".
            if (n.contains("boutique non alimentaire") || n.contains("boutique alimentaire")) {
                shop = true
            }
        }

        return PoiAmenities(
            automat = automat,
            mannedAutomat24h = mannedAutomat24h,
            shop = shop,
            carWash = carWash,
            toilets = toilets,
            restaurant = restaurant,
            food = food,
            snackbar = snackbar,
            wifi = wifi,
            atm = atm,
            adBluePump = adBluePump,
            adBlueCanister = adBlueCanister,
            truckLane = truckLane,
            truckIndicator = truckIndicator,
            truckParking = truckParking,
            truckDiesel = truckDiesel,
            lpg = lpg,
            showers = showers,
            playground = playground,
            drinkingWater = drinkingWater,
            open24h = open24h,
        )
    }

    internal fun isMappedServiceName(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("automate") ||
            n.contains("lavage") ||
            n.contains("toilette") ||
            n.contains("boutique") ||
            n.contains("shop") ||
            n.contains("alimentaire") ||
            n.contains("restauration") ||
            n.contains("restaurant") ||
            n.contains("emporter") ||
            n.contains("wifi") ||
            n.contains("wi-fi") ||
            n.contains("dab") ||
            (n.contains("distributeur") && n.contains("billet")) ||
            n.contains("adblue") ||
            n.contains("poids lourds") ||
            n.contains("piste pl") ||
            n.contains("parking pl") ||
            n.contains("parking poids") ||
            n.contains("gpl") ||
            n.contains("gplc") ||
            n.contains("gaz domestique") ||
            n.contains("butane") ||
            n.contains("propane") ||
            n.contains("douche") ||
            n.contains("aire de jeux") ||
            n.contains("eau potable")
        // gonflage intentionally unmapped → "Autres services"
    }

    private fun parseServiceNames(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }
                is JsonObject -> item["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }

    private data class ScheduleHours(
        val lines: List<String> = emptyList(),
        val monOpen: String? = null,
        val monClose: String? = null,
        val tueOpen: String? = null,
        val tueClose: String? = null,
        val wedOpen: String? = null,
        val wedClose: String? = null,
        val thuOpen: String? = null,
        val thuClose: String? = null,
        val friOpen: String? = null,
        val friClose: String? = null,
        val satOpen: String? = null,
        val satClose: String? = null,
        val sunOpen: String? = null,
        val sunClose: String? = null,
    )

    private fun parseSchedules(element: JsonElement?): ScheduleHours {
        val arr = element as? JsonArray ?: return ScheduleHours()
        if (arr.isEmpty()) return ScheduleHours()

        var monOpen: String? = null
        var monClose: String? = null
        var tueOpen: String? = null
        var tueClose: String? = null
        var wedOpen: String? = null
        var wedClose: String? = null
        var thuOpen: String? = null
        var thuClose: String? = null
        var friOpen: String? = null
        var friClose: String? = null
        var satOpen: String? = null
        var satClose: String? = null
        var sunOpen: String? = null
        var sunClose: String? = null
        val lines = mutableListOf<String>()

        for (item in arr) {
            val obj = item as? JsonObject ?: continue
            val dayName = obj.stringField("name")
                ?: obj.stringField("dayName")
                ?: obj.stringField("day")
                ?: obj["day"]?.jsonPrimitive?.contentOrNull
            val dayNum = obj["day"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: dayIndexFromName(dayName)

            val (open, close) = extractOpenClose(obj)
            if (open != null || close != null) {
                when (dayNum) {
                    1 -> { monOpen = open; monClose = close }
                    2 -> { tueOpen = open; tueClose = close }
                    3 -> { wedOpen = open; wedClose = close }
                    4 -> { thuOpen = open; thuClose = close }
                    5 -> { friOpen = open; friClose = close }
                    6 -> { satOpen = open; satClose = close }
                    7 -> { sunOpen = open; sunClose = close }
                }
                val label = dayName ?: dayNum?.let { "Jour $it" } ?: "Horaires"
                val range = listOfNotNull(open, close).joinToString(" – ")
                if (range.isNotBlank()) lines.add("$label: $range")
            } else {
                // Free-form schedule entry
                obj.stringField("label")?.let { lines.add(it) }
                obj.stringField("text")?.let { lines.add(it) }
            }
        }

        return ScheduleHours(
            lines = lines.distinct(),
            monOpen = monOpen,
            monClose = monClose,
            tueOpen = tueOpen,
            tueClose = tueClose,
            wedOpen = wedOpen,
            wedClose = wedClose,
            thuOpen = thuOpen,
            thuClose = thuClose,
            friOpen = friOpen,
            friClose = friClose,
            satOpen = satOpen,
            satClose = satClose,
            sunOpen = sunOpen,
            sunClose = sunClose,
        )
    }

    private fun extractOpenClose(obj: JsonObject): Pair<String?, String?> {
        val open = obj.stringField("opening_time")
            ?: obj.stringField("openingTime")
            ?: obj.stringField("open")
            ?: obj.stringField("ouverture")
        val close = obj.stringField("closing_time")
            ?: obj.stringField("closingTime")
            ?: obj.stringField("close")
            ?: obj.stringField("fermeture")
        if (open != null || close != null) {
            return normalizeTime(open) to normalizeTime(close)
        }
        val slots = obj["TimeSlots"] as? JsonArray
            ?: obj["timeSlots"] as? JsonArray
            ?: obj["horaires"] as? JsonArray
        val first = slots?.firstOrNull() as? JsonObject
        if (first != null) {
            val o = first.stringField("opening_time")
                ?: first.stringField("openingTime")
                ?: first.stringField("ouverture")
            val c = first.stringField("closing_time")
                ?: first.stringField("closingTime")
                ?: first.stringField("fermeture")
            return normalizeTime(o) to normalizeTime(c)
        }
        return null to null
    }

    private fun dayIndexFromName(name: String?): Int? {
        if (name.isNullOrBlank()) return null
        val n = name.lowercase()
        return when {
            n.startsWith("lun") || n == "1" || n == "monday" -> 1
            n.startsWith("mar") || n == "2" || n == "tuesday" -> 2
            n.startsWith("mer") || n == "3" || n == "wednesday" -> 3
            n.startsWith("jeu") || n == "4" || n == "thursday" -> 4
            n.startsWith("ven") || n == "5" || n == "friday" -> 5
            n.startsWith("sam") || n == "6" || n == "saturday" -> 6
            n.startsWith("dim") || n == "7" || n == "sunday" -> 7
            else -> null
        }
    }

    private fun normalizeTime(value: String?): String? {
        if (value.isNullOrBlank()) return null
        // "06:00:00" → "06:00"
        return value.trim().removeSuffix(":00").take(5).takeIf { it.isNotBlank() }
    }

    private fun JsonObject.booleanField(key: String): Boolean? {
        val el = this[key] ?: return null
        return when (el) {
            is JsonPrimitive -> el.booleanOrNull
                ?: el.contentOrNull?.toBooleanStrictOrNull()
            else -> null
        }
    }

    private fun JsonObject.stringField(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
}
