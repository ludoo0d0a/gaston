package fr.geoking.gaston

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Production feature gates with optional runtime overrides (dev settings).
 *
 * Defaults target a lean FR/LU release. Call [setOverride] / [clearOverrides] from
 * developer UI; [overrides] is observed so Compose can refresh.
 */
object FeatureFlag {

    enum class Key(val default: Boolean, val group: Group) {
        COUNTRY_FR(true, Group.Country),
        COUNTRY_LU(true, Group.Country),
        COUNTRY_BE(false, Group.Country),
        COUNTRY_CH(false, Group.Country),
        COUNTRY_NL(false, Group.Country),
        COUNTRY_DK(false, Group.Country),
        COUNTRY_AT(false, Group.Country),
        COUNTRY_DE(false, Group.Country),
        COUNTRY_GB(false, Group.Country),
        COUNTRY_ES(false, Group.Country),
        COUNTRY_IT(false, Group.Country),
        COUNTRY_HR(false, Group.Country),
        COUNTRY_SI(false, Group.Country),
        COUNTRY_ME(false, Group.Country),
        COUNTRY_MK(false, Group.Country),
        COUNTRY_NO(false, Group.Country),
        COUNTRY_SE(false, Group.Country),
        COUNTRY_PT(false, Group.Country),
        COUNTRY_FI(false, Group.Country),
        COUNTRY_GR(false, Group.Country),
        COUNTRY_IE(false, Group.Country),
        COUNTRY_PL(false, Group.Country),
        COUNTRY_MD(false, Group.Country),
        COUNTRY_RO(false, Group.Country),
        COUNTRY_RS(false, Group.Country),
        COUNTRY_MX(false, Group.Country),
        COUNTRY_AR(false, Group.Country),
        COUNTRY_AU(false, Group.Country),
        COUNTRY_CA(false, Group.Country),
        COUNTRY_US(false, Group.Country),

        RADAR(false, Group.Layer),
        METEO(false, Group.Layer),
        TRAFFIC(false, Group.Layer),

        PHONE_MAP_GOOGLE(false, Group.PhoneMap),
        PHONE_MAP_MAPLIBRE(true, Group.PhoneMap),
        PHONE_MAP_RASTER(false, Group.PhoneMap),
        PHONE_MAP_MAPSFORGE(false, Group.PhoneMap),

        AA_MAP_NATIVE(false, Group.AaMap),
        AA_MAP_RASTER(false, Group.AaMap),
        AA_MAP_MAPLIBRE_SNAPSHOTTER(false, Group.AaMap),
        AA_MAP_MAPLIBRE_VIRTUAL_DISPLAY(false, Group.AaMap),
        AA_MAP_MAPLIBRE_EGL_DIRECT(false, Group.AaMap),
        AA_MAP_MAPBOX(false, Group.AaMap),
        AA_MAP_MAPSFORGE(true, Group.AaMap);

        /** ISO code for [Group.Country] keys, else null. */
        val countryCode: String?
            get() = if (group == Group.Country) name.removePrefix("COUNTRY_") else null
    }

    enum class Group {
        Country,
        Layer,
        PhoneMap,
        AaMap,
    }

    private val _overrides = MutableStateFlow<Map<Key, Boolean>>(emptyMap())
    val overrides: StateFlow<Map<Key, Boolean>> = _overrides.asStateFlow()

    fun resolved(key: Key): Boolean = _overrides.value[key] ?: key.default

    fun isOverridden(key: Key): Boolean = key in _overrides.value

    /**
     * Sets the effective value. When [enabled] matches the compile-time default,
     * the override is cleared so the key returns to stock.
     */
    fun setResolved(key: Key, enabled: Boolean) {
        if (enabled == key.default) {
            clearOverride(key)
        } else {
            _overrides.value = _overrides.value + (key to enabled)
        }
    }

    fun setOverride(key: Key, enabled: Boolean) {
        _overrides.value = _overrides.value + (key to enabled)
    }

    fun clearOverride(key: Key) {
        if (key !in _overrides.value) return
        _overrides.value = _overrides.value - key
    }

    fun clearOverrides() {
        if (_overrides.value.isEmpty()) return
        _overrides.value = emptyMap()
    }

    /** Replace all overrides (e.g. load from prefs). */
    fun replaceOverrides(map: Map<Key, Boolean>) {
        _overrides.value = map.filterKeys { true }
    }

    /** All supported country codes known to Gaston (ParkingRegion + probes). */
    val ALL_COUNTRY_CODES: Set<String> = Key.entries.mapNotNull { it.countryCode }.toSet()

    val COUNTRY_FR: Boolean get() = resolved(Key.COUNTRY_FR)
    val COUNTRY_LU: Boolean get() = resolved(Key.COUNTRY_LU)
    val COUNTRY_BE: Boolean get() = resolved(Key.COUNTRY_BE)
    val COUNTRY_CH: Boolean get() = resolved(Key.COUNTRY_CH)
    val COUNTRY_NL: Boolean get() = resolved(Key.COUNTRY_NL)
    val COUNTRY_DK: Boolean get() = resolved(Key.COUNTRY_DK)
    val COUNTRY_AT: Boolean get() = resolved(Key.COUNTRY_AT)
    val COUNTRY_DE: Boolean get() = resolved(Key.COUNTRY_DE)
    val COUNTRY_GB: Boolean get() = resolved(Key.COUNTRY_GB)
    val COUNTRY_ES: Boolean get() = resolved(Key.COUNTRY_ES)
    val COUNTRY_IT: Boolean get() = resolved(Key.COUNTRY_IT)
    val COUNTRY_HR: Boolean get() = resolved(Key.COUNTRY_HR)
    val COUNTRY_SI: Boolean get() = resolved(Key.COUNTRY_SI)
    val COUNTRY_ME: Boolean get() = resolved(Key.COUNTRY_ME)
    val COUNTRY_MK: Boolean get() = resolved(Key.COUNTRY_MK)
    val COUNTRY_NO: Boolean get() = resolved(Key.COUNTRY_NO)
    val COUNTRY_SE: Boolean get() = resolved(Key.COUNTRY_SE)
    val COUNTRY_PT: Boolean get() = resolved(Key.COUNTRY_PT)
    val COUNTRY_FI: Boolean get() = resolved(Key.COUNTRY_FI)
    val COUNTRY_GR: Boolean get() = resolved(Key.COUNTRY_GR)
    val COUNTRY_IE: Boolean get() = resolved(Key.COUNTRY_IE)
    val COUNTRY_PL: Boolean get() = resolved(Key.COUNTRY_PL)
    val COUNTRY_MD: Boolean get() = resolved(Key.COUNTRY_MD)
    val COUNTRY_RO: Boolean get() = resolved(Key.COUNTRY_RO)
    val COUNTRY_RS: Boolean get() = resolved(Key.COUNTRY_RS)
    val COUNTRY_MX: Boolean get() = resolved(Key.COUNTRY_MX)
    val COUNTRY_AR: Boolean get() = resolved(Key.COUNTRY_AR)
    val COUNTRY_AU: Boolean get() = resolved(Key.COUNTRY_AU)
    val COUNTRY_CA: Boolean get() = resolved(Key.COUNTRY_CA)
    val COUNTRY_US: Boolean get() = resolved(Key.COUNTRY_US)

    fun isCountryEnabled(isoCountryCode: String): Boolean = when (isoCountryCode.trim().uppercase()) {
        "FR" -> COUNTRY_FR
        "LU" -> COUNTRY_LU
        "BE" -> COUNTRY_BE
        "CH" -> COUNTRY_CH
        "NL" -> COUNTRY_NL
        "DK" -> COUNTRY_DK
        "AT" -> COUNTRY_AT
        "DE" -> COUNTRY_DE
        "GB", "UK" -> COUNTRY_GB
        "ES" -> COUNTRY_ES
        "IT" -> COUNTRY_IT
        "HR" -> COUNTRY_HR
        "SI" -> COUNTRY_SI
        "ME" -> COUNTRY_ME
        "MK" -> COUNTRY_MK
        "NO" -> COUNTRY_NO
        "SE" -> COUNTRY_SE
        "PT" -> COUNTRY_PT
        "FI" -> COUNTRY_FI
        "GR" -> COUNTRY_GR
        "IE" -> COUNTRY_IE
        "PL" -> COUNTRY_PL
        "MD" -> COUNTRY_MD
        "RO" -> COUNTRY_RO
        "RS" -> COUNTRY_RS
        "MX" -> COUNTRY_MX
        "AR" -> COUNTRY_AR
        "AU", "AU-WA" -> COUNTRY_AU
        "CA" -> COUNTRY_CA
        "US" -> COUNTRY_US
        else -> false
    }

    fun enabledCountryCodes(): Set<String> =
        ALL_COUNTRY_CODES.filter { isCountryEnabled(it) }.toSet()

    val RADAR: Boolean get() = resolved(Key.RADAR)
    val METEO: Boolean get() = resolved(Key.METEO)
    val TRAFFIC: Boolean get() = resolved(Key.TRAFFIC)

    val PHONE_MAP_GOOGLE: Boolean get() = resolved(Key.PHONE_MAP_GOOGLE)
    val PHONE_MAP_MAPLIBRE: Boolean get() = resolved(Key.PHONE_MAP_MAPLIBRE)
    val PHONE_MAP_RASTER: Boolean get() = resolved(Key.PHONE_MAP_RASTER)
    val PHONE_MAP_MAPSFORGE: Boolean get() = resolved(Key.PHONE_MAP_MAPSFORGE)

    val AA_MAP_NATIVE: Boolean get() = resolved(Key.AA_MAP_NATIVE)
    val AA_MAP_RASTER: Boolean get() = resolved(Key.AA_MAP_RASTER)
    val AA_MAP_MAPLIBRE_SNAPSHOTTER: Boolean get() = resolved(Key.AA_MAP_MAPLIBRE_SNAPSHOTTER)
    val AA_MAP_MAPLIBRE_VIRTUAL_DISPLAY: Boolean get() = resolved(Key.AA_MAP_MAPLIBRE_VIRTUAL_DISPLAY)
    val AA_MAP_MAPLIBRE_EGL_DIRECT: Boolean get() = resolved(Key.AA_MAP_MAPLIBRE_EGL_DIRECT)
    val AA_MAP_MAPBOX: Boolean get() = resolved(Key.AA_MAP_MAPBOX)
    val AA_MAP_MAPSFORGE: Boolean get() = resolved(Key.AA_MAP_MAPSFORGE)
}
