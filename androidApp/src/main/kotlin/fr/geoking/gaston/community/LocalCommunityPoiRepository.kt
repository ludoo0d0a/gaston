package fr.geoking.gaston.community

import fr.geoking.gaston.community.db.CommunityPoiEntity
import fr.geoking.gaston.community.db.HiddenPoiEntity
import fr.geoking.gaston.community.storage.CommunityPoiStorage
import fr.geoking.gaston.poi.Poi
import kotlin.math.cos

private const val COMMUNITY_POI_ID_PREFIX = "community_"

fun isCommunityPoiId(id: String): Boolean = id.startsWith(COMMUNITY_POI_ID_PREFIX)

fun communityPoiId(): String = "$COMMUNITY_POI_ID_PREFIX${java.util.UUID.randomUUID()}"

class LocalCommunityPoiRepository(
    private val storage: CommunityPoiStorage
) : CommunityPoiRepository {

    override suspend fun getCommunityPoisInArea(lat: Double, lng: Double, radiusKm: Double): List<Poi> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        val all = storage.getCommunityPois()
        val inArea = all.filter { e ->
            e.latitude in (lat - latDelta)..(lat + latDelta) &&
            e.longitude in (lng - lngDelta)..(lng + lngDelta)
        }
        return inArea.map { it.toPoi() }
    }

    override suspend fun getCommunityLinkedOfficialIdsInArea(lat: Double, lng: Double, radiusKm: Double): Set<String> {
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        val all = storage.getCommunityPois()
        return all
            .filter { e ->
                e.latitude in (lat - latDelta)..(lat + latDelta) &&
                e.longitude in (lng - lngDelta)..(lng + lngDelta)
            }
            .mapNotNull { it.linkedOfficialId }
            .toSet()
    }

    override suspend fun getHiddenOfficialIds(): Set<String> =
        storage.getHiddenPois().map { it.externalPoiId }.toSet()

    override suspend fun addCommunityPoi(poi: Poi, linkedOfficialId: String?) {
        val id = if (isCommunityPoiId(poi.id)) poi.id else communityPoiId()
        val entity = poi.toEntity(id, linkedOfficialId)
        val list = storage.getCommunityPois().toMutableList()
        list.removeAll { it.id == id }
        list.add(entity)
        storage.saveCommunityPois(list)
    }

    override suspend fun updateCommunityPoi(id: String, poi: Poi) {
        if (!isCommunityPoiId(id)) return
        val list = storage.getCommunityPois().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val existing = list[idx]
        val entity = poi.toEntity(id, null).copy(createdAt = existing.createdAt, updatedAt = System.currentTimeMillis())
        list[idx] = entity
        storage.saveCommunityPois(list)
    }

    override suspend fun removeCommunityPoi(id: String) {
        if (!isCommunityPoiId(id)) return
        val list = storage.getCommunityPois().filter { it.id != id }
        storage.saveCommunityPois(list)
    }

    override suspend fun hideOfficialPoi(externalPoiId: String) {
        val list = storage.getHiddenPois().toMutableList()
        if (list.any { it.externalPoiId == externalPoiId }) return
        list.add(HiddenPoiEntity(id = System.currentTimeMillis(), externalPoiId = externalPoiId, createdAt = System.currentTimeMillis()))
        storage.saveHiddenPois(list)
    }

    override suspend fun unhideOfficialPoi(externalPoiId: String) {
        val list = storage.getHiddenPois().filter { it.externalPoiId != externalPoiId }
        storage.saveHiddenPois(list)
    }

    override suspend fun getCommunityPoi(id: String): Poi? =
        storage.getCommunityPois().find { it.id == id }?.toPoi()
}

private fun CommunityPoiEntity.toPoi(): Poi = Poi(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    brand = brand,
    isElectric = isElectric,
    powerKw = powerKw,
    operator = operator,
    chargePointCount = chargePointCount
)

private fun Poi.toEntity(id: String, linkedOfficialId: String?): CommunityPoiEntity {
    val now = System.currentTimeMillis()
    return CommunityPoiEntity(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        brand = brand,
        isElectric = isElectric,
        powerKw = powerKw,
        operator = operator,
        chargePointCount = chargePointCount,
        linkedOfficialId = linkedOfficialId,
        createdAt = now,
        updatedAt = now
    )
}
