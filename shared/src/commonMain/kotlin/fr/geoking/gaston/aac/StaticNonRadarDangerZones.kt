package fr.geoking.gaston.aac

/**
 * Non-radar danger zones for AAC Level A mix (not 100% radar-derived).
 *
 * Sample / placeholder set based on well-known high-vigilance corridors.
 * Open-data path for production expansion:
 * - BAAC / ONISR accidentalité (data.gouv.fr)
 * - Zones de vigilance / points noirs locaux (collectivités)
 * No community police-control feeds (L. 130-11 process not implemented).
 */
object StaticNonRadarDangerZones {
    const val SOURCE = "StaticAccidentProneSample"

    fun all(): List<DangerZone> = listOf(
        // A6 / Île-de-France — heightened vigilance sample (urban approach)
        DangerZoneFactory.accidentProne(
            id = "fr_acc_sample_a6_orly",
            latitude = 48.7430,
            longitude = 2.3700,
            radiusMeters = DangerZoneDistances.EXTRA_URBAN_METERS,
            speedLimitKmH = 90,
            source = SOURCE,
            roadClass = RoadNetworkClass.ExtraUrban,
        ),
        // Périphérique Paris sud — urban vigilance
        DangerZoneFactory.accidentProne(
            id = "fr_acc_sample_periph_sud",
            latitude = 48.8200,
            longitude = 2.3600,
            radiusMeters = DangerZoneDistances.URBAN_METERS,
            speedLimitKmH = 70,
            source = SOURCE,
            roadClass = RoadNetworkClass.Urban,
        ),
        // A7 Valence sector — motorway vigilance sample
        DangerZoneFactory.accidentProne(
            id = "fr_acc_sample_a7_valence",
            latitude = 44.9330,
            longitude = 4.8920,
            radiusMeters = DangerZoneDistances.MOTORWAY_METERS,
            speedLimitKmH = 130,
            source = SOURCE,
            roadClass = RoadNetworkClass.Motorway,
        ),
    )

    fun near(latitude: Double, longitude: Double, radiusKm: Double): List<DangerZone> {
        return all().filter { zone ->
            zone.distanceMetersFrom(latitude, longitude) <= radiusKm * 1000.0 + zone.radiusMeters
        }
    }
}
