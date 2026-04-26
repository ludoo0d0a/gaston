package fr.geoking.gaston.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdMobBanner(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adRequest = remember { AdRequest.Builder().build() }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            // Standard banner height (50dp). AdView will size itself to match.
            .height(50.dp),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(adRequest)
            }
        },
        update = { view ->
            // Ensure ID stays in sync if caller changes it.
            if (view.adUnitId != adUnitId) {
                view.adUnitId = adUnitId
                view.loadAd(adRequest)
            }
        }
    )
}

