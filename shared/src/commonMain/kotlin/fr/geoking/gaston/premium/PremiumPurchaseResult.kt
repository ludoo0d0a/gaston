package fr.geoking.gaston.premium

sealed class PremiumPurchaseResult {
    data object Success : PremiumPurchaseResult()
    data object Cancelled : PremiumPurchaseResult()
    data object Pending : PremiumPurchaseResult()
    data class Error(val message: String) : PremiumPurchaseResult()
}
