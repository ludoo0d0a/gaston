package fr.geoking.gaston.premium

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.models.EntitlementInfo
import com.revenuecat.purchases.kmp.models.Package
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.StoreTransaction
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class BillingManager {
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun updatePremiumStatus(entitlements: Map<String, EntitlementInfo>) {
        _isPremium.value = entitlements[ENTITLEMENT_ID]?.isActive == true
    }

    suspend fun refreshStatus() {
        try {
            val customerInfo = suspendCoroutine<CustomerInfo> { continuation ->
                Purchases.sharedInstance.getCustomerInfo(
                    onSuccess = { continuation.resume(it) },
                    onError = { error -> continuation.resumeWithException(Exception(error.message)) }
                )
            }
            updatePremiumStatus(customerInfo.entitlements.active)
        } catch (e: Exception) {
            // Handle error
        }
    }

    suspend fun purchasePremium(): Result<Unit> {
        return try {
            val pkg = fetchPremiumPackage() ?: return Result.failure(Exception("Premium package not found"))
            val customerInfo = suspendCoroutine<CustomerInfo> { continuation ->
                Purchases.sharedInstance.purchase(
                    packageToPurchase = pkg,
                    onSuccess = { _, info -> continuation.resume(info) },
                    onError = { error, _ -> continuation.resumeWithException(Exception(error.message)) }
                )
            }
            updatePremiumStatus(customerInfo.entitlements.active)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchPremiumPackage(): Package? {
        val offerings = suspendCoroutine<Offerings> { continuation ->
            Purchases.sharedInstance.getOfferings(
                onSuccess = { continuation.resume(it) },
                onError = { error -> continuation.resumeWithException(Exception(error.message)) }
            )
        }
        // Try to find a package with identifier "premium" or containing a product with identifier "premium"
        return offerings.current?.availablePackages?.find { it.identifier == "premium" }
            ?: offerings.all.values.flatMap { it.availablePackages }.find { it.identifier == "premium" }
            ?: offerings.current?.availablePackages?.firstOrNull()
    }

    companion object {
        const val ENTITLEMENT_ID = "premium"
    }
}
