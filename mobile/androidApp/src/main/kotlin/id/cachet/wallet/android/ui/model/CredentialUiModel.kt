package id.cachet.wallet.android.ui.model

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection

data class CredentialCardUi(
    val localId: String,
    val displayName: String,
    val issuerLine: String,
    val freshnessLabel: String,
    val isRevoked: Boolean,
    val cachetType: CachetType?,
    val trustStatus: TrustStatus,
    val predicates: List<String>,
    val sharesSummary: String
)

data class VaultSummaryUi(
    val totalCount: Int,
    val verifiedCount: Int,
    val pendingCount: Int,
    val revokedCount: Int = 0
)

data class CachPackUi(
    val id: String = "",
    val question: String,
    val description: String,
    val proofCount: Int,
    val cachetType: CachetType
)

data class CachetDetailUi(
    val localId: String,
    val displayName: String,
    val cachetType: CachetType,
    val trustStatus: TrustStatus,
    val issuedDate: String,
    val expiresDate: String,
    val issuer: String,
    val predicates: List<RequestPredicate>,
    val relatedActivity: List<HistoryEntry>,
    val isRevoked: Boolean = false,
    val revokedDate: String? = null,
    val keyAlias: String? = null
)
