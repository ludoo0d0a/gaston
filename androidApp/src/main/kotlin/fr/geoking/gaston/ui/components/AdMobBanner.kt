package fr.geoking.gaston.ui.components

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.geoking.gaston.SettingsManager
import org.koin.compose.koinInject
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun AdMobBanner(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val settingsManager = koinInject<SettingsManager>()
    val settings by settingsManager.settings.collectAsState()

    if (settings.hasPremiumFeatures) return

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
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.i("AdMobBanner", "Banner loaded (unitId=$adUnitId)")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e(
                            "AdMobBanner",
                            "Banner failed (unitId=$adUnitId) code=${error.code} domain=${error.domain} message=${error.message} " +
                                "responseInfo=${error.responseInfo}"
                        )
                    }
                }
                loadAd(adRequest)
            }
        },
        update = { view ->
            // Ensure ID stays in sync if caller changes it.
            if (view.adUnitId != adUnitId) {
                view.adUnitId = adUnitId
                Log.i("AdMobBanner", "Banner unit id changed -> reload (unitId=$adUnitId)")
                view.loadAd(adRequest)
            }
        }
    )
}

