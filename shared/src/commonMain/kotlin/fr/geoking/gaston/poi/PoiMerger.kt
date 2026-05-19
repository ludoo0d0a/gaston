package fr.geoking.gaston.poi

import fr.geoking.gaston.api.routex.PoiAmenities
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
    private const val MERGE_DISTANCE_WITH_NAME_METERS = 300.0
    // Distance threshold for merge with same brand.
    private const val MERGE_DISTANCE_WITH_BRAND_METERS = 300.0
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

    /**
     * Incremental merge into a map: uses the station ID for fast lookup and falls back to
     * proximity matching.
     */
    fun mergeInto(existing: MutableMap<String, Poi>, incoming: List<Poi>) {
        for (poi in incoming) {
            val match = existing[poi.id] ?: existing.values.find { isSamePoi(it, poi) }
            if (match != null) {
                existing[match.id] = mergeTwo(match, poi)
            } else {
                existing[poi.id] = poi
            }
        }
    }

    private fun isSamePoi(a: Poi, b: Poi): Boolean {
        if (a.id == b.id) return true

        // Fast reject on approximate deltas before doing haversine.
        val maxDist = maxOf(MERGE_DISTANCE_WITH_NAME_METERS, MERGE_DISTANCE_WITH_BRAND_METERS)
        val latDeltaMeters = abs(a.latitude - b.latitude) * 111_000.0
        if (latDeltaMeters > maxDist * 1.2) return false

        val lonDeltaMeters =
            abs(a.longitude - b.longitude) * 111_000.0 * cos(((a.latitude + b.latitude) / 2.0) * PI / 180.0)
        if (lonDeltaMeters > maxDist * 1.2) return false

        val distMeters = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

        // 1. Unconditional merge if extremely close (within 50m)
        if (distMeters <= MERGE_DISTANCE_METERS) return true

        // 2. Merge if fairly close (within 300m) AND same brand
        if (distMeters <= MERGE_DISTANCE_WITH_BRAND_METERS) {
            val brandA = BrandRegistry.findBrand(a.name, a.brand)
            val brandB = BrandRegistry.findBrand(b.name, b.brand)
            if (brandA != null && brandA == brandB) return true
        }

        // 3. Merge if fairly close (within 300m) AND name matches (80%)
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

        // Priority to Gas/Irve for visual identity (branding, icon).
        // If any merged POI is a station, the primary category MUST be Gas or Irve.
        val mergedPoiCategory = when {
            existing.poiCategory == PoiCategory.Gas || incoming.poiCategory == PoiCategory.Gas -> PoiCategory.Gas
            existing.poiCategory == PoiCategory.Irve || incoming.poiCategory == PoiCategory.Irve -> PoiCategory.Irve
            else -> existing.poiCategory ?: incoming.poiCategory ?: if (mergedIsElectric) PoiCategory.Irve else PoiCategory.Gas
        }

        // All other categories (from both POIs and their extras) are collected into extraCategories.
        val mergedExtraCategories = (
            (existing.extraCategories + (existing.poiCategory?.let { setOf(it) } ?: emptySet())) +
            (incoming.extraCategories + (incoming.poiCategory?.let { setOf(it) } ?: emptySet()))
        ).filter { it != mergedPoiCategory }.toSet()

        val mergedFuelPrices = mergeFuelPrices(existing.fuelPrices, incoming.fuelPrices)

        // Prices not updated since 4 weeks are considered definitely closed.
        val staleClosed = mergedFuelPrices != null && mergedFuelPrices.isNotEmpty() &&
            mergedFuelPrices.all { it.updatedAt != null && isStale(it.updatedAt, weeks = 4) }

        val mergedIsClosed = existing.isClosed || incoming.isClosed || staleClosed
        val mergedIrveDetails = mergeIrveDetails(existing.irveDetails, incoming.irveDetails)
        val mergedAmenities = mergeAmenities(existing.amenities, incoming.amenities)
        val mergedRestaurantDetails = mergeRestaurantDetails(existing.restaurantDetails, incoming.restaurantDetails)

        val mergedSources = mergeSources(existing.source, incoming.source)
        val mergedSourceUpdates = mergeSourceUpdates(existing.sourceUpdates, incoming.sourceUpdates)

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
            extraCategories = mergedExtraCategories,
            fuelPrices = mergedFuelPrices,
            isClosed = mergedIsClosed,
            irveDetails = mergedIrveDetails,
            amenities = mergedAmenities,
            restaurantDetails = mergedRestaurantDetails,
            // Prefer richer/non-null display fields.
            name = if (isBetterName(incoming.name, existing.name)) incoming.name else existing.name,
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
            sourceUpdates = mergedSourceUpdates,
        )
    }

    private fun preferNonBlank(a: String?, b: String?): String? {
        return a?.takeIf { it.isNotBlank() } ?: b?.takeIf { it.isNotBlank() }
    }

    private fun isBetterName(candidate: String, current: String): Boolean {
        if (candidate.isBlank()) return false
        if (current.isBlank()) return true
        if (candidate.equals(current, ignoreCase = true)) return false

        val candLower = candidate.lowercase()
        val currLower = current.lowercase()

        // 1. Generic labels to avoid
        val generic = setOf("station", "route", "autoroute")
        if (currLower in generic && candLower !in generic) return true
        if (candLower in generic && currLower !in generic) return false

        // 2. Brand presence: Prefer names that contain a known brand
        val brandCand = BrandRegistry.findBrand(candidate, null)
        val brandCurr = BrandRegistry.findBrand(current, null)
        if (brandCand != null && brandCurr == null) return true
        if (brandCand == null && brandCurr != null) return false

        // 3. Length heuristic: longer names are often more descriptive
        return candLower.length > currLower.length
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

    private fun mergeSourceUpdates(a: Map<String, String>?, b: Map<String, String>?): Map<String, String>? {
        if (a == null) return b
        if (b == null) return a
        val merged = a.toMutableMap()
        for ((source, timestamp) in b) {
            val existing = merged[source]
            if (existing == null || timestamp > existing) {
                merged[source] = timestamp
            }
        }
        return merged.takeIf { it.isNotEmpty() }
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

    private fun mergeAmenities(a: PoiAmenities?, b: PoiAmenities?): PoiAmenities? {
        if (a == null) return b
        if (b == null) return a
        return PoiAmenities(
            manned24h = mergeBool(a.manned24h, b.manned24h),
            mannedAutomat24h = mergeBool(a.mannedAutomat24h, b.mannedAutomat24h),
            automat = mergeBool(a.automat, b.automat),
            motorwayIndicator = mergeBool(a.motorwayIndicator, b.motorwayIndicator),
            restaurant = mergeBool(a.restaurant, b.restaurant),
            shop = mergeBool(a.shop, b.shop),
            snackbar = mergeBool(a.snackbar, b.snackbar),
            carWash = mergeBool(a.carWash, b.carWash),
            showers = mergeBool(a.showers, b.showers),
            adBluePump = mergeBool(a.adBluePump, b.adBluePump),
            r4tNetwork = mergeBool(a.r4tNetwork, b.r4tNetwork),
            carVignette = mergeBool(a.carVignette, b.carVignette),
            highspeedDiesel = mergeBool(a.highspeedDiesel, b.highspeedDiesel),
            truckIndicator = mergeBool(a.truckIndicator, b.truckIndicator),
            truckParking = mergeBool(a.truckParking, b.truckParking),
            truckDiesel = mergeBool(a.truckDiesel, b.truckDiesel),
            truckLane = mergeBool(a.truckLane, b.truckLane),
            dieselBio = mergeBool(a.dieselBio, b.dieselBio),
            hvo100 = mergeBool(a.hvo100, b.hvo100),
            lng = mergeBool(a.lng, b.lng),
            lpg = mergeBool(a.lpg, b.lpg),
            cng = mergeBool(a.cng, b.cng),
            adBlueCanister = mergeBool(a.adBlueCanister, b.adBlueCanister),
            toilets = mergeBool(a.toilets, b.toilets),
            drinkingWater = mergeBool(a.drinkingWater, b.drinkingWater),
            food = mergeBool(a.food, b.food),
            wifi = mergeBool(a.wifi, b.wifi),
            atm = mergeBool(a.atm, b.atm),
            playground = mergeBool(a.playground, b.playground),
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

    private fun mergeBool(a: Boolean?, b: Boolean?): Boolean? {
        return when {
            a == true || b == true -> true
            a == false || b == false -> false
            else -> null
        }
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
            val now = kotlin.time.Clock.System.now()
            val updatedInstant = fr.geoking.gaston.shared.datetime.DateTimeUtils.parseFlexible(updatedAt) ?: return false
            val diff = now - updatedInstant
            diff.inWholeDays > (weeks * 7)
        } catch (e: Exception) {
            false
        }
    }
}

