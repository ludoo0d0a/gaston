package fr.geoking.gaston.ui.map.maplibre

import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.MapBaseView
import fr.geoking.gaston.MapTheme

/**
 * Resolves the MapLibre style for the phone map basemap selector.
 * Streets uses the configured OpenFreeMap vector theme; photo / hybrid / terrain use raster JSON.
 */
data class MapLibreStyleSpec(
    val styleUrl: String? = null,
    val styleJson: String? = null,
) {
    init {
        require(styleUrl != null || styleJson != null) { "Need styleUrl or styleJson" }
    }
}

private const val ESRI_IMAGERY =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
private const val ESRI_TOPO =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}"
private const val ESRI_REFERENCE_LABELS =
    "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"

fun resolvePhoneMapLibreStyle(settings: AppSettings, preferDark: Boolean = false): MapLibreStyleSpec {
    return when (settings.mapBaseView) {
        MapBaseView.Streets -> {
            val theme = if (settings.mapTheme.isDark == preferDark) {
                settings.mapTheme
            } else {
                if (preferDark) MapTheme.Dark else MapTheme.Voyager
            }
            MapLibreStyleSpec(styleUrl = theme.styleUrl)
        }
        MapBaseView.Satellite -> MapLibreStyleSpec(styleJson = singleRasterStyle(ESRI_IMAGERY, "Esri"))
        MapBaseView.Terrain -> MapLibreStyleSpec(styleJson = singleRasterStyle(ESRI_TOPO, "Esri"))
        MapBaseView.Hybrid -> MapLibreStyleSpec(
            styleJson = hybridRasterStyle(
                imageryUrl = ESRI_IMAGERY,
                labelsUrl = ESRI_REFERENCE_LABELS,
            )
        )
    }
}

private fun singleRasterStyle(tileUrl: String, attribution: String): String = """
{
  "version": 8,
  "name": "raster",
  "sources": {
    "raster": {
      "type": "raster",
      "tiles": ["$tileUrl"],
      "tileSize": 256,
      "attribution": "$attribution"
    }
  },
  "layers": [
    { "id": "raster", "type": "raster", "source": "raster" }
  ]
}
""".trimIndent()

private fun hybridRasterStyle(imageryUrl: String, labelsUrl: String): String = """
{
  "version": 8,
  "name": "hybrid",
  "sources": {
    "imagery": {
      "type": "raster",
      "tiles": ["$imageryUrl"],
      "tileSize": 256,
      "attribution": "Esri"
    },
    "labels": {
      "type": "raster",
      "tiles": ["$labelsUrl"],
      "tileSize": 256,
      "attribution": "Esri"
    }
  },
  "layers": [
    { "id": "imagery", "type": "raster", "source": "imagery" },
    { "id": "labels", "type": "raster", "source": "labels" }
  ]
}
""".trimIndent()
