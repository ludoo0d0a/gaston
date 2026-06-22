package fr.geoking.gaston.premium

import com.revenuecat.purchases.kmp.models.EntitlementInfo

/**
 * Actionable subscription context derived from RevenueCat [EntitlementInfo].
 * Access is still gated on [EntitlementInfo.isActive]; these notices explain *why* the user
 * should act while access remains (grace period, canceled-but-paid-through, etc.).
 */
sealed class PremiumSubscriptionNotice {
    data object BillingIssue : PremiumSubscriptionNotice()

    /** User canceled or subscription will not renew; [expirationDateMillis] is when access ends. */
    data class ExpiresOn(val expirationDateMillis: Long) : PremiumSubscriptionNotice()
}

internal fun premiumNoticeFrom(entitlement: EntitlementInfo?): PremiumSubscriptionNotice? {
    if (entitlement == null || !entitlement.isActive) return null
    if (entitlement.billingIssueDetectedAtMillis != null) {
        return PremiumSubscriptionNotice.BillingIssue
    }
    val expirationMillis = entitlement.expirationDateMillis
    if (entitlement.unsubscribeDetectedAtMillis != null && expirationMillis != null) {
        return PremiumSubscriptionNotice.ExpiresOn(expirationMillis)
    }
    if (!entitlement.willRenew && expirationMillis != null) {
        return PremiumSubscriptionNotice.ExpiresOn(expirationMillis)
    }
    return null
}
