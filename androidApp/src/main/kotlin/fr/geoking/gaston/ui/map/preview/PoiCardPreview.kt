package fr.geoking.gaston.ui.map.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.ui.map.PoiDetailCard

private data class PoiCardPreviewSample(
    val highlightedFuelIds: Set<String> = emptySet(),
    val highlightedPowerLevels: Set<Int> = emptySet(),
    val selected: Boolean = false
)

@Composable
private fun PoiCardsStripPreviewBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0F172A,
    name = "POI cards — 6 (brands, fuels, IRVE)",
    widthDp = 980,
    heightDp = 328
)
@Composable
private fun PoiCardsPreviewDiverse() {
    val pois = PreviewPoiSamples.diverseCardPois()
    // Gas cards need isSelected so merged POI expands and fuel-row highlights show; IRVE uses compact power tint.
    val highlights = listOf(
        PoiCardPreviewSample(highlightedFuelIds = setOf("gazole"), selected = true),
        PoiCardPreviewSample(highlightedFuelIds = setOf("sp98"), selected = true),
        PoiCardPreviewSample(highlightedPowerLevels = setOf(200, 300, 20, 50, 100, 150), selected = false),
        PoiCardPreviewSample(highlightedPowerLevels = setOf(20, 50, 100, 150, 200), selected = false),
        PoiCardPreviewSample(highlightedFuelIds = setOf("sp95"), selected = true),
        PoiCardPreviewSample(highlightedFuelIds = setOf("e85"), selected = true)
    )
    PoiCardsStripPreviewBox {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(pois, key = { _, p -> p.id }) { index, poi ->
                val h = highlights.getOrElse(index) { PoiCardPreviewSample() }
                PoiDetailCard(
                    poi = poi,
                    highlightedFuelIds = h.highlightedFuelIds,
                    highlightedPowerLevels = h.highlightedPowerLevels,
                    onNavigate = {},
                    onLocate = {},
                    onShowDetails = {},
                    isSelected = h.selected
                )
            }
        }
    }
}
