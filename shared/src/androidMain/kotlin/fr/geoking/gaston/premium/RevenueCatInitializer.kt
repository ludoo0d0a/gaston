package fr.geoking.gaston.premium

import android.content.Context
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

object RevenueCatInitializer {
    fun initialize(context: Context, apiKey: String) {
        Purchases.configure(
            PurchasesConfiguration(apiKey) {
                // The SDK correctly handles the Android Application Context when called from androidMain.
            }
        )
    }
}
