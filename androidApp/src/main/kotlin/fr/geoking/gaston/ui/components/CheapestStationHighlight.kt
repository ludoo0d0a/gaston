package fr.geoking.gaston.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.R
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.ColorHelper

object CheapestStationHighlight {
    fun minFuelPrice(stations: List<Poi>, fuelIds: Set<String>): Double? {
        if (fuelIds.isEmpty()) return null
        return stations.mapNotNull { poi ->
            poi.fuelPrices
                ?.filter { !it.outOfStock && MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
                ?.minByOrNull { it.price }
                ?.price
        }.minOrNull()
    }

    fun isCheapestFuelStation(poi: Poi, minPrice: Double?, fuelIds: Set<String>): Boolean {
        if (minPrice == null || fuelIds.isEmpty()) return false
        return poi.fuelPrices?.any {
            !it.outOfStock &&
                MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds &&
                it.price == minPrice
        } == true
    }

    @Composable
    fun containerColor(isCheapest: Boolean): Color {
        if (!isCheapest) return MaterialTheme.colorScheme.surface
        return if (isSystemInDarkTheme()) Color(0xFF422006) else Color(0xFFFFFBEB)
    }

    val borderColor: Color
        get() = ColorHelper.ColorRank1

    val badgeBackgroundColor: Color
        get() = ColorHelper.ColorRank1

    val badgeTextColor: Color
        get() = Color(0xFF422006)
}

@Composable
fun CheapestStationBadge(modifier: Modifier = Modifier) {
    Surface(
        color = CheapestStationHighlight.badgeBackgroundColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        Text(
            stringResource(R.string.route_cheapest_badge),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = CheapestStationHighlight.badgeTextColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CheapestHighlightCard(
    isCheapest: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CheapestStationHighlight.containerColor(isCheapest)
        ),
        modifier = modifier,
        border = if (isCheapest) {
            BorderStroke(2.dp, CheapestStationHighlight.borderColor)
        } else {
            null
        }
    ) {
        Column(content = content)
    }
}
