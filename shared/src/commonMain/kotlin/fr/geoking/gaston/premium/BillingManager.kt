package fr.geoking.gaston.premium

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.EntitlementInfo
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager {
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _subscriptionNotice = MutableStateFlow<PremiumSubscriptionNotice?>(null)
    val subscriptionNotice: StateFlow<PremiumSubscriptionNotice?> = _subscriptionNotice.asStateFlow()

    /**
     * Registers a [PurchasesDelegate] so entitlement changes (purchase, restore, renewal,
     * billing recovery, expiration) propagate without manual polling.
     */
    fun attachCustomerInfoListener() {
        Purchases.sharedInstance.delegate = object : PurchasesDelegate {
            override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
                applyCustomerInfo(customerInfo)
            }

            override fun onPurchasePromoProduct(
                product: StoreProduct,
                startPurchase: (
                    onError: (PurchasesError, Boolean) -> Unit,
                    onSuccess: (StoreTransaction, CustomerInfo) -> Unit
                ) -> Unit,
            ) {
                // App Store promotional purchases only.
            }
        }
    }

    suspend fun refreshStatus() {
        try {
            val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
            applyCustomerInfo(customerInfo)
        } catch (_: PurchasesException) {
            // Keep last known cached state.
        }
    }

    suspend fun purchasePremium(): PremiumPurchaseResult {
        return try {
            val pkg = fetchPremiumPackage() ?: return PremiumPurchaseResult.Error("Premium package not found")
            val result = Purchases.sharedInstance.awaitPurchase(packageToPurchase = pkg)
            applyCustomerInfo(result.customerInfo)
            PremiumPurchaseResult.Success
        } catch (e: PurchasesTransactionException) {
            handlePurchaseException(e)
        } catch (e: PurchasesException) {
            PremiumPurchaseResult.Error(userFacingMessage(e.error))
        }
    }

    suspend fun restorePurchases(): PremiumPurchaseResult {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitRestore()
            applyCustomerInfo(customerInfo)
            if (customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true) {
                PremiumPurchaseResult.Success
            } else {
                PremiumPurchaseResult.Error("No active Premium subscription found on this account.")
            }
        } catch (e: PurchasesException) {
            PremiumPurchaseResult.Error(userFacingMessage(e.error))
        }
    }

    private suspend fun handlePurchaseException(e: PurchasesTransactionException): PremiumPurchaseResult {
        if (e.userCancelled) return PremiumPurchaseResult.Cancelled
        return when (e.code) {
            PurchasesErrorCode.PaymentPendingError -> PremiumPurchaseResult.Pending
            PurchasesErrorCode.ProductAlreadyPurchasedError -> {
                refreshStatus()
                PremiumPurchaseResult.Success
            }
            else -> PremiumPurchaseResult.Error(userFacingMessage(e.error))
        }
    }

    private fun applyCustomerInfo(customerInfo: CustomerInfo) {
        val entitlement = customerInfo.entitlements[ENTITLEMENT_ID]
        updatePremiumStatus(customerInfo.entitlements.active)
        _subscriptionNotice.value = premiumNoticeFrom(entitlement)
    }

    fun updatePremiumStatus(entitlements: Map<String, EntitlementInfo>) {
        _isPremium.value = entitlements[ENTITLEMENT_ID]?.isActive == true
    }

    private suspend fun fetchPremiumPackage(): Package? {
        val offerings = Purchases.sharedInstance.awaitOfferings()
        return offerings.current?.availablePackages?.find { it.identifier == "premium" }
            ?: offerings.all.values.flatMap { it.availablePackages }.find { it.identifier == "premium" }
            ?: offerings.current?.availablePackages?.firstOrNull()
    }

    companion object {
        const val ENTITLEMENT_ID = "premium"

        fun userFacingMessage(error: PurchasesError): String = when (error.code) {
            PurchasesErrorCode.NetworkError,
            PurchasesErrorCode.OfflineConnectionError ->
                "Please check your internet connection and try again."
            PurchasesErrorCode.StoreProblemError ->
                "There was a problem with Google Play. Please try again."
            PurchasesErrorCode.ProductAlreadyPurchasedError ->
                "You already have this subscription."
            PurchasesErrorCode.PaymentPendingError ->
                "Your payment is being processed. Premium will unlock when it completes."
            PurchasesErrorCode.PurchaseNotAllowedError ->
                "Purchases are not allowed on this device or account."
            else -> "Something went wrong. Please try again."
        }
    }
}
