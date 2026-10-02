package fr.geoking.gaston.feature.settings

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.BulkFileDownloadPolicy
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.DEFAULT_EV_RANGE_KM
import fr.geoking.gaston.FuelCard
import fr.geoking.gaston.UserVehicle
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.poi.PoiProviderType
import kotlinx.coroutines.tasks.await

private const val TAG = "FirestoreSettingsSync"
private const val COLLECTION_SETTINGS = "user_settings"
private const val DOCUMENT_ID = "app_settings"

class FirestoreSettingsSync(
    private val firestore: FirebaseFirestore?,
    private val firebaseAuth: FirebaseAuth?
) {
    suspend fun uploadSettings(settings: AppSettings) {
        val userId = firebaseAuth?.currentUser?.uid ?: return
        val db = firestore ?: return
        try {
            val data = settingsToMap(settings)
            db.collection(COLLECTION_SETTINGS)
                .document(userId)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "uploadSettings: success")
        } catch (e: Exception) {
            Log.e(TAG, "uploadSettings: failed", e)
        }
    }

    suspend fun downloadAndMerge(localSettings: AppSettings): AppSettings? {
        val userId = firebaseAuth?.currentUser?.uid ?: return null
        val db = firestore ?: return null
        try {
            val doc = db.collection(COLLECTION_SETTINGS)
                .document(userId)
                .get()
                .await()

            if (!doc.exists()) {
                Log.d(TAG, "downloadAndMerge: no remote settings found")
                return null
            }

            val remoteData = doc.data ?: return null
            return mergeSettings(localSettings, remoteData)
        } catch (e: Exception) {
            Log.e(TAG, "downloadAndMerge: failed", e)
            return null
        }
    }

    private fun settingsToMap(s: AppSettings): Map<String, Any?> {
        return mapOf(
            "vehicleBrand" to s.vehicleBrand,
            "vehicleModel" to s.vehicleModel,
            "vehicleEnergy" to s.vehicleEnergy,
            "vehicleGasTypes" to s.vehicleGasTypes.toList(),
            "vehiclePowerLevels" to s.vehiclePowerLevels.toList(),
            "fuelCard" to s.fuelCard.name,
            "activeVehicleId" to s.activeVehicleId,
            "vehicles" to s.vehicles.map { v ->
                mapOf(
                    "id" to v.id,
                    "brand" to v.brand,
                    "model" to v.model,
                    "plate" to v.plate,
                    "color" to v.color,
                    "serialNumber" to v.serialNumber,
                    "fuelCard" to v.fuelCard.name,
                    "energy" to v.energy,
                    "vehicleType" to v.vehicleType.name,
                    "gasTypes" to v.gasTypes.toList(),
                    "powerLevels" to v.powerLevels.toList(),
                    "gasTankCapacityLiters" to v.gasTankCapacityLiters,
                    "gasConsumptionLper100km" to v.gasConsumptionLper100km,
                    "batteryCapacityKwh" to v.batteryCapacityKwh,
                    "evRangeKm" to v.evRangeKm,
                    "evConsumptionKwhPer100km" to v.evConsumptionKwhPer100km,
                    "insurerName" to v.insurerName,
                    "policyNumber" to v.policyNumber,
                    "greenCardNumber" to v.greenCardNumber,
                    "insurerPhone" to v.insurerPhone,
                    "agencyName" to v.agencyName,
                    "agencyPhone" to v.agencyPhone,
                )
            },
            "useVehicleFilter" to s.useVehicleFilter,
            "selectedPoiProviders" to s.selectedPoiProviders.map { it.name },
            "bulkFileDownloadPolicy" to s.bulkFileDownloadPolicy.name,
            "selectedMapEnergyTypes" to s.selectedMapEnergyTypes.toList(),
            "mapEnseigneType" to s.mapEnseigneType,
            "mapBrands" to s.mapBrands.toList(),
            "selectedMapServices" to s.selectedMapServices.toList(),
            "mapPowerLevels" to s.mapPowerLevels.toList(),
            "mapIrveOperators" to s.mapIrveOperators.toList(),
            "selectedMapConnectorTypes" to s.selectedMapConnectorTypes.toList(),
            "mapTrafficEnabled" to s.mapTrafficEnabled,
            "evRangeKm" to s.evRangeKm,
            "evConsumptionKwhPer100km" to s.evConsumptionKwhPer100km,
            "selectedOverpassAmenityTypes" to s.selectedOverpassAmenityTypes.toList(),
            "vehicleType" to s.vehicleType.name,
            "carMapMode" to s.carMapMode.name,
            "offlinePmtilesPath" to s.offlinePmtilesPath,
            "offlineMapsforgePath" to s.offlineMapsforgePath,
            "isPremium" to s.isPremium,
            "favoriteLocations" to s.favoriteLocations.map { mapOf("label" to it.label, "latitude" to it.latitude, "longitude" to it.longitude) },
            "lastAcceptedDisclaimerVersion" to s.lastAcceptedDisclaimerVersion
        )
    }

    private fun mergeSettings(local: AppSettings, remote: Map<String, Any?>): AppSettings {
        // "Merge: local first. If local data, use local first"
        // Interpretation: if the local value is NOT the default value (i.e. user modified it), keep local.
        // Otherwise, take from remote.

        val default = AppSettings()

        fun <T> pick(current: T, remoteVal: Any?, defaultVal: T, parser: (Any) -> T): T {
            return if (current != defaultVal) {
                current
            } else {
                remoteVal?.let { try { parser(it) } catch(e: Exception) { defaultVal } } ?: defaultVal
            }
        }

        fun parseString(v: Any) = v as String
        fun parseInt(v: Any) = (v as Long).toInt()
        fun parseBoolean(v: Any) = v as Boolean
        fun <E : Enum<E>> parseEnum(v: Any, enumClass: Class<E>): E = java.lang.Enum.valueOf(enumClass, v as String)
        fun parseStringSet(v: Any) = (v as List<*>).filterIsInstance<String>().toSet()
        fun parseIntSet(v: Any) = (v as List<*>).filterIsInstance<Long>().map { it.toInt() }.toSet()
        fun parsePoiProviderSet(v: Any) = (v as List<*>).filterIsInstance<String>().mapNotNull {
            try { PoiProviderType.valueOf(it) } catch(e: Exception) { null }
        }.toSet()
        fun parseGeocodedPlaceList(v: Any) = (v as List<*>).filterIsInstance<Map<String, Any>>().map {
            fr.geoking.gaston.api.geocoding.GeocodedPlace(
                label = it["label"] as String,
                latitude = (it["latitude"] as? Double) ?: (it["latitude"] as? Long)?.toDouble() ?: 0.0,
                longitude = (it["longitude"] as? Double) ?: (it["longitude"] as? Long)?.toDouble() ?: 0.0
            )
        }

        fun parseFloat(v: Any?): Float? = when (v) {
            null -> null
            is Double -> v.toFloat()
            is Float -> v
            is Long -> v.toFloat()
            is Int -> v.toFloat()
            is String -> v.toFloatOrNull()
            else -> null
        }

        fun parseVehicles(v: Any): List<UserVehicle> = (v as List<*>).filterIsInstance<Map<*, *>>().mapNotNull { raw ->
            val id = raw["id"] as? String ?: return@mapNotNull null
            val fuel = try {
                FuelCard.valueOf(raw["fuelCard"] as? String ?: FuelCard.None.name)
            } catch (_: Exception) {
                FuelCard.None
            }
            val vehicleType = try {
                VehicleType.valueOf(raw["vehicleType"] as? String ?: VehicleType.Car.name)
            } catch (_: Exception) {
                VehicleType.Car
            }
            UserVehicle(
                id = id,
                brand = raw["brand"] as? String ?: "",
                model = raw["model"] as? String ?: "",
                plate = raw["plate"] as? String ?: "",
                color = raw["color"] as? String ?: "",
                serialNumber = raw["serialNumber"] as? String ?: "",
                fuelCard = fuel,
                energy = raw["energy"] as? String ?: "gas",
                vehicleType = vehicleType,
                gasTypes = (raw["gasTypes"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
                powerLevels = (raw["powerLevels"] as? List<*>)?.mapNotNull { level ->
                    when (level) {
                        is Long -> level.toInt()
                        is Int -> level
                        is Double -> level.toInt()
                        else -> null
                    }
                }?.toSet().orEmpty(),
                gasTankCapacityLiters = parseFloat(raw["gasTankCapacityLiters"]),
                gasConsumptionLper100km = parseFloat(raw["gasConsumptionLper100km"]),
                batteryCapacityKwh = parseFloat(raw["batteryCapacityKwh"]),
                evRangeKm = when (val range = raw["evRangeKm"]) {
                    is Long -> range.toInt()
                    is Int -> range
                    is Double -> range.toInt()
                    else -> DEFAULT_EV_RANGE_KM
                },
                evConsumptionKwhPer100km = parseFloat(raw["evConsumptionKwhPer100km"]),
                insurerName = raw["insurerName"] as? String ?: "",
                policyNumber = raw["policyNumber"] as? String ?: "",
                greenCardNumber = raw["greenCardNumber"] as? String ?: "",
                insurerPhone = raw["insurerPhone"] as? String ?: "",
                agencyName = raw["agencyName"] as? String ?: "",
                agencyPhone = raw["agencyPhone"] as? String ?: "",
            )
        }

        return local.copy(
            vehicleBrand = pick(local.vehicleBrand, remote["vehicleBrand"], default.vehicleBrand, ::parseString),
            vehicleModel = pick(local.vehicleModel, remote["vehicleModel"], default.vehicleModel, ::parseString),
            vehicleEnergy = pick(local.vehicleEnergy, remote["vehicleEnergy"], default.vehicleEnergy, ::parseString),
            vehicleGasTypes = pick(local.vehicleGasTypes, remote["vehicleGasTypes"], default.vehicleGasTypes, ::parseStringSet),
            vehiclePowerLevels = pick(local.vehiclePowerLevels, remote["vehiclePowerLevels"], default.vehiclePowerLevels, ::parseIntSet),
            fuelCard = pick(local.fuelCard, remote["fuelCard"], default.fuelCard) { parseEnum(it, FuelCard::class.java) },
            vehicles = pick(local.vehicles, remote["vehicles"], default.vehicles, ::parseVehicles),
            activeVehicleId = pick(local.activeVehicleId, remote["activeVehicleId"], default.activeVehicleId, ::parseString),
            useVehicleFilter = pick(local.useVehicleFilter, remote["useVehicleFilter"], default.useVehicleFilter, ::parseBoolean),
            selectedPoiProviders = pick(local.selectedPoiProviders, remote["selectedPoiProviders"], default.selectedPoiProviders, ::parsePoiProviderSet),
            bulkFileDownloadPolicy = pick(
                local.bulkFileDownloadPolicy,
                remote["bulkFileDownloadPolicy"] ?: remote["bulkFileDownloadsWifiOnly"],
                default.bulkFileDownloadPolicy,
            ) { v ->
                when (v) {
                    is Boolean -> if (v) BulkFileDownloadPolicy.WifiOnly else BulkFileDownloadPolicy.Allowed
                    is String -> try {
                        BulkFileDownloadPolicy.valueOf(v)
                    } catch (_: Exception) {
                        default.bulkFileDownloadPolicy
                    }
                    else -> default.bulkFileDownloadPolicy
                }
            },
            selectedMapEnergyTypes = pick(local.selectedMapEnergyTypes, remote["selectedMapEnergyTypes"], default.selectedMapEnergyTypes, ::parseStringSet),
            mapEnseigneType = pick(local.mapEnseigneType, remote["mapEnseigneType"], default.mapEnseigneType, ::parseString),
            mapBrands = pick(local.mapBrands, remote["mapBrands"], default.mapBrands, ::parseStringSet),
            selectedMapServices = pick(local.selectedMapServices, remote["selectedMapServices"], default.selectedMapServices, ::parseStringSet),
            mapPowerLevels = pick(local.mapPowerLevels, remote["mapPowerLevels"], default.mapPowerLevels, ::parseIntSet),
            mapIrveOperators = pick(local.mapIrveOperators, remote["mapIrveOperators"], default.mapIrveOperators, ::parseStringSet),
            selectedMapConnectorTypes = pick(local.selectedMapConnectorTypes, remote["selectedMapConnectorTypes"], default.selectedMapConnectorTypes, ::parseStringSet),
            mapTrafficEnabled = pick(local.mapTrafficEnabled, remote["mapTrafficEnabled"], default.mapTrafficEnabled, ::parseBoolean),
            evRangeKm = pick(local.evRangeKm, remote["evRangeKm"], default.evRangeKm, ::parseInt),
            evConsumptionKwhPer100km = pick(local.evConsumptionKwhPer100km, remote["evConsumptionKwhPer100km"], default.evConsumptionKwhPer100km) { (it as Double).toFloat() },
            selectedOverpassAmenityTypes = pick(local.selectedOverpassAmenityTypes, remote["selectedOverpassAmenityTypes"], default.selectedOverpassAmenityTypes, ::parseStringSet),
            vehicleType = pick(local.vehicleType, remote["vehicleType"], default.vehicleType) { parseEnum(it, VehicleType::class.java) },
            carMapMode = pick(local.carMapMode, remote["carMapMode"], default.carMapMode) { parseEnum(it, CarMapMode::class.java) },
            offlinePmtilesPath = pick(local.offlinePmtilesPath, remote["offlinePmtilesPath"], default.offlinePmtilesPath, ::parseString),
            offlineMapsforgePath = pick(local.offlineMapsforgePath, remote["offlineMapsforgePath"], default.offlineMapsforgePath, ::parseString),
            isPremium = pick(local.isPremium, remote["isPremium"], default.isPremium, ::parseBoolean),
            favoriteLocations = pick(local.favoriteLocations, remote["favoriteLocations"], default.favoriteLocations, ::parseGeocodedPlaceList),
            lastAcceptedDisclaimerVersion = pick(local.lastAcceptedDisclaimerVersion, remote["lastAcceptedDisclaimerVersion"], default.lastAcceptedDisclaimerVersion, ::parseInt)
        )
    }
}
