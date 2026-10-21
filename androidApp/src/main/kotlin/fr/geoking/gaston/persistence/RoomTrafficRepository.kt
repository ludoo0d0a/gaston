package fr.geoking.gaston.persistence

import fr.geoking.gaston.api.traffic.Bbox
import fr.geoking.gaston.api.traffic.TrafficEvent
import fr.geoking.gaston.api.traffic.TrafficRepository
import fr.geoking.gaston.api.traffic.TrafficSeverity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomTrafficRepository(
    private val dao: TrafficEventDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : TrafficRepository {

    override suspend fun getTrafficInBbox(
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double,
        maxEvents: Int
    ): List<TrafficEvent> {
        val entities = dao.getEventsInBbox(latMin, lonMin, latMax, lonMax, maxEvents)
        return entities.map { entityToDomain(it) }
    }

    override suspend fun isSourceFresh(sourceId: String, ttlMs: Long): Boolean {
        val lastFetch = dao.getLastFetchTimestamp(sourceId) ?: return false
        val now = System.currentTimeMillis()
        return (now - lastFetch) < ttlMs
    }

    override suspend fun replaceEventsForSource(
        sourceId: String,
        events: List<TrafficEvent>,
        fetchedAtMs: Long
    ) {
        dao.deleteEventsForSource(sourceId)
        val entities = events.mapIndexed { index, event ->
            domainToEntity(sourceId, event, index, fetchedAtMs)
        }
        if (entities.isNotEmpty()) {
            dao.insertEvents(entities)
        }
    }

    override suspend fun updateZonePoints(
        eventId: String,
        sourceId: String,
        zonePoints: List<Pair<Double, Double>>
    ) {
        val primaryKey = "${sourceId}_$eventId"
        val jsonStr = json.encodeToString(zonePoints)
        dao.updateZonePoints(primaryKey, jsonStr)
    }

    override suspend fun clear(sourceId: String?) {
        if (sourceId != null) {
            dao.deleteEventsForSource(sourceId)
        } else {
            dao.clearAll()
        }
    }

    private fun domainToEntity(
        sourceId: String,
        event: TrafficEvent,
        index: Int,
        fetchedAtMs: Long
    ): TrafficEventEntity {
        val eventId = event.sourceId ?: "event_$index"
        val primaryKey = "${sourceId}_$eventId"
        val bbox = event.bbox ?: Bbox(
            latMin = event.latitude ?: 0.0,
            lonMin = event.longitude ?: 0.0,
            latMax = event.latitude ?: 0.0,
            lonMax = event.longitude ?: 0.0
        )
        val zonePointsJson = event.zonePoints?.let { json.encodeToString(it) }

        return TrafficEventEntity(
            primaryKey = primaryKey,
            sourceId = sourceId,
            eventId = event.sourceId,
            roadRef = event.roadRef,
            direction = event.direction,
            severity = event.severity.name,
            message = event.message,
            travelTimeSeconds = event.travelTimeSeconds,
            latMin = bbox.latMin,
            lonMin = bbox.lonMin,
            latMax = bbox.latMax,
            lonMax = bbox.lonMax,
            latitude = event.latitude,
            longitude = event.longitude,
            zonePointsJson = zonePointsJson,
            updatedAtMs = event.updatedAt,
            fetchedAtMs = fetchedAtMs
        )
    }

    private fun entityToDomain(entity: TrafficEventEntity): TrafficEvent {
        val severity = runCatching { TrafficSeverity.valueOf(entity.severity) }.getOrDefault(TrafficSeverity.Unknown)
        val bbox = Bbox(
            latMin = entity.latMin,
            lonMin = entity.lonMin,
            latMax = entity.latMax,
            lonMax = entity.lonMax
        )
        val zonePoints: List<Pair<Double, Double>>? = entity.zonePointsJson?.let {
            runCatching { json.decodeFromString<List<Pair<Double, Double>>>(it) }.getOrNull()
        }

        return TrafficEvent(
            roadRef = entity.roadRef,
            direction = entity.direction,
            severity = severity,
            message = entity.message,
            travelTimeSeconds = entity.travelTimeSeconds,
            bbox = bbox,
            sourceId = entity.eventId,
            updatedAt = entity.updatedAtMs,
            latitude = entity.latitude,
            longitude = entity.longitude,
            zonePoints = zonePoints
        )
    }
}
