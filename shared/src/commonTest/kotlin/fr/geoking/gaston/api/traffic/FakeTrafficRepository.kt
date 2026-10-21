package fr.geoking.gaston.api.traffic

class FakeTrafficRepository(
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : TrafficRepository {
    private val storage = mutableMapOf<String, MutableList<TrafficEvent>>()
    private val timestamps = mutableMapOf<String, Long>()

    override suspend fun getTrafficInBbox(
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double,
        maxEvents: Int
    ): List<TrafficEvent> {
        return storage.values.flatten().filter { e ->
            val b = e.bbox ?: return@filter false
            b.latMin <= latMax && b.latMax >= latMin && b.lonMin <= lonMax && b.lonMax >= lonMin
        }.take(maxEvents)
    }

    override suspend fun isSourceFresh(sourceId: String, ttlMs: Long): Boolean {
        val ts = timestamps[sourceId] ?: return false
        return clockMs() - ts < ttlMs
    }

    override suspend fun replaceEventsForSource(
        sourceId: String,
        events: List<TrafficEvent>,
        fetchedAtMs: Long
    ) {
        storage[sourceId] = events.toMutableList()
        timestamps[sourceId] = fetchedAtMs
    }

    override suspend fun updateZonePoints(
        eventId: String,
        sourceId: String,
        zonePoints: List<Pair<Double, Double>>
    ) {
        val list = storage[sourceId] ?: return
        val idx = list.indexOfFirst { it.sourceId == eventId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(zonePoints = zonePoints)
        }
    }

    override suspend fun clear(sourceId: String?) {
        if (sourceId != null) {
            storage.remove(sourceId)
            timestamps.remove(sourceId)
        } else {
            storage.clear()
            timestamps.clear()
        }
    }
}
