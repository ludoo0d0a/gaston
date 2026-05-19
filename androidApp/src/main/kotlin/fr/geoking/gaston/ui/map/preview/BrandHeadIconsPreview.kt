package fr.geoking.gaston.ui.map.preview

import fr.geoking.gaston.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.ui.BrandHelper

private const val ICONS_PER_ROW = 6

@Composable
private fun BrandIconCell(label: String, resId: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = label,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE2E8F0),
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun BrandIconWrappedSection(
    title: String,
    entries: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        entries.chunked(ICONS_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { (label, resId) ->
                    BrandIconCell(
                        label = label,
                        resId = resId,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(ICONS_PER_ROW - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Studio preview: [BrandHelper.brandIcons] for electric brands then gas brands,
 * up to [ICONS_PER_ROW] icons per line (wrapping).
 */
@Composable
private fun BrandFlatIconsWrapped(modifier: Modifier = Modifier) {
    val electric = remember { BrandHelper.getElectricBrandIconEntries() }
    val gas = remember { BrandHelper.getGasBrandIconEntries() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFF0F172A))
    ) {
        BrandIconWrappedSection(title = stringResource(R.string.energy_electric), entries = electric)
        BrandIconWrappedSection(title = stringResource(R.string.poi_gas), entries = gas)
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF0F172A,
    name = "Brand icons — electric + gas (6 per row)",
    widthDp = 420,
    heightDp = 640
)
@Composable
private fun BrandFlatIconsPreview() {
    MaterialTheme {
        Surface(color = Color(0xFF0F172A)) {
            BrandFlatIconsWrapped()
        }
    }
}
