package id.cachet.wallet.android.ui.model

import id.cachet.wallet.android.ui.components.BadgeType
import id.cachet.wallet.android.ui.components.TrustStatus

data class CredentialCardUi(
    val localId: String,
    val displayName: String,
    val issuerLine: String,
    val freshnessLabel: String,
    val isRevoked: Boolean,
    val badgeType: BadgeType?,
    val trustStatus: TrustStatus,
    val predicates: List<String>,
    val sharesSummary: String
)

data class VaultSummaryUi(
    val totalCount: Int,
    val verifiedCount: Int,
    val pendingCount: Int
)

data class TrustPackUi(
    val question: String,
    val description: String,
    val proofCount: Int,
    val badgeType: BadgeType
)
