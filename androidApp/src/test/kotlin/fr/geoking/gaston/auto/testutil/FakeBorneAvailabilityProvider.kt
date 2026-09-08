package fr.geoking.gaston.auto.testutil

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability

class FakeBorneAvailabilityProvider : BorneAvailabilityProvider {
    override suspend fun getAvailability(latitude: Double, longitude: Double, radiusKm: Int): List<PdcAvailability> =
        emptyList()
}
