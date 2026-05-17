package id.cachet.wallet.android.trusttrail.model

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.model.RequestPredicate
import id.cachet.wallet.trusttrail.strength.Tier

/**
 * UI model for the behavioral cachet detail screen (v2).
 * Bridges credential + TrustTrail evidence data to the screen state.
 */
data class BehavioralCachetDetailUi(
    val localId: String,
    val displayName: String,
    val cachetType: CachetType = CachetType.TRUSTED_HOST,
    val strength: Float,
    val tier: Tier?,
    val issuedDate: String,
    val issuer: String,
    val foundationStatus: String,
    val predicates: List<RequestPredicate>,
    val evidencePlatforms: List<PlatformContributionUi>,
)

data class PlatformContributionUi(
    val platformName: String,
    val evidenceCount: Int,
    val contributionPercent: Int,
)
