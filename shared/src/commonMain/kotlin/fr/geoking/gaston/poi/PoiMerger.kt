package fr.geoking.gaston.poi

import fr.geoking.gaston.api.routex.RoutexSiteDetails
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deduplicates POIs that represent the same physical place coming from different sources.
 *
 * Match rule: close enough (distance) OR (fairly close AND similar enough name).
 * When merging, combines the underlying data (fuel prices, details, connector types, etc.)
 * into a single [Poi].
 */
object PoiMerger {
    // Distance threshold for unconditional merge.
    private const val MERGE_DISTANCE_METERS = 50.0
    // Distance threshold for merge with name matching.
    private const val MERGE_DISTANCE_WITH_NAME_METERS = 250.0
    private const val NAME_TOKEN_MIN_LENGTH = 2
    private const val NAME_SIMILARITY_MIN = 0.8

    fun mergePois(pois: List<Poi>): List<Poi> {
        if (pois.isEmpty()) return emptyList()
        // Deterministic iteration order helps keep IDs stable across merges.
        val ordered = pois.sortedBy { it.id }
        val merged = ordered.toMutableList()
        var i = 0
        while (i < merged.size) {
            val current = merged[i]
            var j = i + 1
            while (j < merged.size) {
                val other = merged[j]
                if (isSamePoi(current, other)) {
                    merged[i] = mergeTwo(current, other)
                    merged.removeAt(j)
                    // Don't increment j; list shrank.
                    continue
                }
                j++
            }
            i++
        }
        return merged
    }

    /**
     * Incremental merge: keeps the existing list elements stable and merges incoming elements
     * into the closest matches.
     */
    fun mergeInto(existing: List<Poi>, incoming: List<Poi>): List<Poi> {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) return mergePois(incoming)

        val merged = existing.toMutableList()
        for (poi in incoming) {
            val matchIndex = merged.indexOfFirst { candidate -> isSamePoi(candidate, poi) }
            if (matchIndex >= 0) {
                merged[matchIndex] = mergeTwo(merged[matchIndex], poi)
            } else {
                merged.add(poi)
            }
        }
        return merged
    }

    private fun isSamePoi(a: Poi, b: Poi): Boolean {
        if (a.id == b.id) return true

        // Fast reject on approximate deltas before doing haversine.
        val latDeltaMeters = abs(a.latitude - b.latitude) * 111_000.0
        if (latDeltaMeters > MERGE_DISTANCE_WITH_NAME_METERS * 1.2) return false

        val lonDeltaMeters =
            abs(a.longitude - b.longitude) * 111_000.0 * cos(((a.latitude + b.latitude) / 2.0) * PI / 180.0)
        if (lonDeltaMeters > MERGE_DISTANCE_WITH_NAME_METERS * 1.2) return false

        val distMeters = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

        // 1. Unconditional merge if extremely close (within 50m)
        if (distMeters <= MERGE_DISTANCE_METERS) return true

        // 2. Merge if fairly close (within 250m) AND name matches (80%)
        if (distMeters <= MERGE_DISTANCE_WITH_NAME_METERS) {
            return namesSimilarEnough(a, b)
        }

        return false
    }

    private fun namesSimilarEnough(a: Poi, b: Poi): Boolean {
        val na = normalizeNameForMatch(buildMatchName(a))
        val nb = normalizeNameForMatch(buildMatchName(b))
        if (na.isBlank() || nb.isBlank()) return false

        val tokensA = tokenSet(na)
        val tokensB = tokenSet(nb)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return false

        val intersection = tokensA.intersect(tokensB)
        if (intersection.isEmpty()) return false
        // Similarity based on overlap vs the larger token set.
        val similarity = intersection.size.toDouble() / maxOf(tokensA.size, tokensB.size).toDouble()
        return similarity >= NAME_SIMILARITY_MIN
    }

    private fun buildMatchName(p: Poi): String {
        // Prefer siteName when available, but still include name.
        val site = p.siteName?.takeIf { it.isNotBlank() }
        return if (site != null) "$site ${p.name}" else p.name
    }

    private fun tokenSet(normalized: String): Set<String> {
        return normalized
            .split(' ')
            .map { it.trim() }
            .filter { it.length >= NAME_TOKEN_MIN_LENGTH }
            .toSet()
    }

    private fun normalizeNameForMatch(s: String): String {
        var out = s.lowercase()

        // Lightweight diacritics folding (common French letters).
        // (Avoids non-common APIs from KMP commonMain like java.text.Normalizer.)
        out = out
            .replace('à', 'a')
            .replace('á', 'a')
            .replace('â', 'a')
            .replace('ä', 'a')
            .replace('ã', 'a')
            .replace('å', 'a')
            .replace('ç', 'c')
            .replace('è', 'e')
            .replace('é', 'e')
            .replace('ê', 'e')
            .replace('ë', 'e')
            .replace('ì', 'i')
            .replace('í', 'i')
            .replace('î', 'i')
            .replace('ï', 'i')
            .replace('ñ', 'n')
            .replace('ò', 'o')
            .replace('ó', 'o')
            .replace('ô', 'o')
            .replace('ö', 'o')
            .replace('õ', 'o')
            .replace('ù', 'u')
            .replace('ú', 'u')
            .replace('û', 'u')
            .replace('ü', 'u')
            .replace('ý', 'y')
            .replace('ÿ', 'y')
            .replace('œ', 'o')
            .replace('æ', 'a')

        out = out.replace(Regex("[^a-z0-9\\s]"), " ")
        out = out.replace(Regex("\\s+"), " ").trim()
        return out
    }

    private fun mergeTwo(existing: Poi, incoming: Poi): Poi {
        val mergedIsElectric = existing.isElectric || incoming.isElectric
        val mergedPoiCategory = existing.poiCategory ?: incoming.poiCategory ?: if (mergedIsElectric) PoiCategory.Irve else PoiCategory.Gas

        val mergedFuelPrices = mergeFuelPrices(existing.fuelPrices, incoming.fuelPrices)

        // Prices not updated since 4 weeks are considered definitely closed.
        val staleClosed = mergedFuelPrices != null && mergedFuelPrices.isNotEmpty() &&
            mergedFuelPrices.all { it.updatedAt != null && isStale(it.updatedAt, weeks = 4) }

        val mergedIsClosed = existing.isClosed || incoming.isClosed || staleClosed
        val mergedIrveDetails = mergeIrveDetails(existing.irveDetails, incoming.irveDetails)
        val mergedRoutexDetails = mergeRoutexDetails(existing.routexDetails, incoming.routexDetails)
        val mergedRestaurantDetails = mergeRestaurantDetails(existing.restaurantDetails, incoming.restaurantDetails)

        val mergedSources = mergeSources(existing.source, incoming.source)

        val brandExisting = BrandRegistry.findBrand(existing.name, existing.brand)
        val brandIncoming = BrandRegistry.findBrand(incoming.name, incoming.brand)

        val mergedBrand = when {
            isBetterBrand(brandIncoming, brandExisting) -> brandIncoming
            else -> brandExisting ?: existing.brand
        }

        return existing.copy(
            // Keep coordinates from the "existing" entry for stable marker placement.
            // They are already close (see isSamePoi).
            isElectric = mergedIsElectric,
            poiCategory = mergedPoiCategory,
            fuelPrices = mergedFuelPrices,
            isClosed = mergedIsClosed,
            irveDetails = mergedIrveDetails,
            routexDetails = mergedRoutexDetails,
            restaurantDetails = mergedRestaurantDetails,
            // Prefer richer/non-null display fields.
            name = if (existing.name.isNotBlank()) existing.name else incoming.name,
            address = if (existing.address.isNotBlank()) existing.address else incoming.address,
            siteName = preferNonBlank(existing.siteName, incoming.siteName),
            brand = mergedBrand,
            addressLocal = preferNonBlank(existing.addressLocal, incoming.addressLocal),
            postcode = preferNonBlank(existing.postcode, incoming.postcode),
            countryLocal = preferNonBlank(existing.countryLocal, incoming.countryLocal),
            townLocal = preferNonBlank(existing.townLocal, incoming.townLocal),
            powerKw = existing.powerKw ?: incoming.powerKw,
            operator = existing.operator ?: incoming.operator,
            isOnHighway = existing.isOnHighway || incoming.isOnHighway,
            chargePointCount = mergeMaxOrNull(existing.chargePointCount, incoming.chargePointCount),
            // Connector / fuel price details are merged above.
            source = mergedSources,
        )
    }

    private fun preferNonBlank(a: String?, b: String?): String? {
        return a?.takeIf { it.isNotBlank() } ?: b?.takeIf { it.isNotBlank() }
    }

    private fun isBetterBrand(candidate: String?, current: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        if (current.isNullOrBlank()) return true

        // If they resolve to the same normalized brand name, no one is "better"
        if (candidate.equals(current, ignoreCase = true)) return false

        val hasIconCandidate = BrandRegistry.hasIcon(candidate)
        val hasIconCurrent = BrandRegistry.hasIcon(current)

        // 1. Priority to brands with icons
        if (hasIconCandidate && !hasIconCurrent) return true
        if (!hasIconCandidate && hasIconCurrent) return false

        val candLower = candidate.lowercase()
        val currLower = current.lowercase()

        // 2. Generic labels to avoid
        val generic = setOf("station", "independant", "independant (gms)", "sans enseigne", "autoroute", "route")
        if (currLower in generic && candLower !in generic) return true
        if (candLower in generic && currLower !in generic) return false

        // 3. Length heuristic: if current is short and candidate is longer, it might be more descriptive
        if (currLower.length < 3 && candLower.length >= 3) return true

        return false
    }

    private fun mergeMaxOrNull(a: Int?, b: Int?): Int? {
        return when {
            a == null && b == null -> null
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
    }

    private fun mergeSources(a: String?, b: String?): String? {
        val parts = listOfNotNull(a, b)
            .map { s -> s.split("+").map { part -> part.trim() }.filter { part -> part.isNotBlank() } }
            .flatten()
            .distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" + ")
    }

    private fun mergeFuelPrices(a: List<FuelPrice>?, b: List<FuelPrice>?): List<FuelPrice>? {
        if (a == null || a.isEmpty()) return b
        if (b == null || b.isEmpty()) return a

        val merged = mutableMapOf<String, FuelPrice>()
        for (fp in a) merged[fp.fuelName] = fp
        for (fp in b) {
            val existing = merged[fp.fuelName]
            merged[fp.fuelName] = if (existing == null) fp else mergeFuelPrice(existing, fp)
        }
        return merged.values.toList()
    }

    private fun mergeFuelPrice(a: FuelPrice, b: FuelPrice): FuelPrice {
        val chooseB = when {
            a.updatedAt != null && b.updatedAt != null -> b.updatedAt >= a.updatedAt
            a.updatedAt == null && b.updatedAt != null -> true
            a.updatedAt != null && b.updatedAt == null -> false
            else -> false
        }
        val picked = if (chooseB) b else a
        val other = if (chooseB) a else b
        return picked.copy(outOfStock = a.outOfStock || b.outOfStock, price = picked.price)
    }

    private fun mergeIrveDetails(a: IrveDetails?, b: IrveDetails?): IrveDetails? {
        if (a == null) return b
        if (b == null) return a
        return a.copy(
            connectorTypes = a.connectorTypes + b.connectorTypes,
            // Prefer latest non-null values when merging "live" availability details.
            availableConnectors = b.availableConnectors ?: a.availableConnectors,
            totalConnectors = b.totalConnectors ?: a.totalConnectors,
            tarification = b.tarification ?: a.tarification,
            gratuit = b.gratuit ?: a.gratuit,
            openingHours = b.openingHours ?: a.openingHours,
            reservation = b.reservation ?: a.reservation,
            paymentActe = b.paymentActe ?: a.paymentActe,
            paymentCb = b.paymentCb ?: a.paymentCb,
            paymentAutre = b.paymentAutre ?: a.paymentAutre,
            conditionAcces = b.conditionAcces ?: a.conditionAcces,
            // Keep other fields from whichever is non-null.
        )
    }

    private fun mergeRoutexDetails(a: RoutexSiteDetails?, b: RoutexSiteDetails?): RoutexSiteDetails? {
        if (a == null) return b
        if (b == null) return a
        return RoutexSiteDetails(
            manned24h = a.manned24h ?: b.manned24h,
            mannedAutomat24h = a.mannedAutomat24h ?: b.mannedAutomat24h,
            automat = a.automat ?: b.automat,
            motorwayIndicator = a.motorwayIndicator ?: b.motorwayIndicator,
            restaurant = a.restaurant ?: b.restaurant,
            shop = a.shop ?: b.shop,
            snackbar = a.snackbar ?: b.snackbar,
            carWash = a.carWash ?: b.carWash,
            showers = a.showers ?: b.showers,
            adBluePump = a.adBluePump ?: b.adBluePump,
            r4tNetwork = a.r4tNetwork ?: b.r4tNetwork,
            carVignette = a.carVignette ?: b.carVignette,
            highspeedDiesel = a.highspeedDiesel ?: b.highspeedDiesel,
            truckIndicator = a.truckIndicator ?: b.truckIndicator,
            truckParking = a.truckParking ?: b.truckParking,
            truckDiesel = a.truckDiesel ?: b.truckDiesel,
            truckLane = a.truckLane ?: b.truckLane,
            dieselBio = a.dieselBio ?: b.dieselBio,
            hvo100 = a.hvo100 ?: b.hvo100,
            lng = a.lng ?: b.lng,
            lpg = a.lpg ?: b.lpg,
            cng = a.cng ?: b.cng,
            adBlueCanister = a.adBlueCanister ?: b.adBlueCanister,
            monOpenFuel = a.monOpenFuel ?: b.monOpenFuel,
            monCloseFuel = a.monCloseFuel ?: b.monCloseFuel,
            tueOpenFuel = a.tueOpenFuel ?: b.tueOpenFuel,
            tueCloseFuel = a.tueCloseFuel ?: b.tueCloseFuel,
            wedOpenFuel = a.wedOpenFuel ?: b.wedOpenFuel,
            wedCloseFuel = a.wedCloseFuel ?: b.wedCloseFuel,
            thuOpenFuel = a.thuOpenFuel ?: b.thuOpenFuel,
            thuCloseFuel = a.thuCloseFuel ?: b.thuCloseFuel,
            friOpenFuel = a.friOpenFuel ?: b.friOpenFuel,
            friCloseFuel = a.friCloseFuel ?: b.friCloseFuel,
            satOpenFuel = a.satOpenFuel ?: b.satOpenFuel,
            satCloseFuel = a.satCloseFuel ?: b.satCloseFuel,
            sunOpenFuel = a.sunOpenFuel ?: b.sunOpenFuel,
            sunCloseFuel = a.sunCloseFuel ?: b.sunCloseFuel,
            open24h = a.open24h ?: b.open24h,
            openingHoursFuel = (a.openingHoursFuel + b.openingHoursFuel).distinct()
        )
    }

    private fun mergeRestaurantDetails(a: RestaurantDetails?, b: RestaurantDetails?): RestaurantDetails? {
        if (a == null) return b
        if (b == null) return a
        return a.copy(
            openingHours = a.openingHours ?: b.openingHours,
            cuisine = a.cuisine ?: b.cuisine,
            brand = a.brand ?: b.brand,
            isFastFood = a.isFastFood || b.isFastFood
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0 // meters
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun isStale(updatedAt: String, weeks: Int): Boolean {
        return try {
            val now = Clock.System.now()
            val updatedInstant = parseFlexible(updatedAt) ?: return false
            val diff = now - updatedInstant
            diff.inWholeDays > (weeks * 7)
        } catch (e: Exception) {
            false
        }
    }

    private fun parseFlexible(dateStr: String): Instant? {
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
}

