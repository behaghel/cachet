package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection
import id.cachet.wallet.android.ui.model.*

/** Vault with an expired credential — exercises the Expired badge in the vault grid. */
object ExpiredScenario : DemoScenario {
    override val name = "expired"

    override val credentials = listOf(
        CredentialCardUi(
            localId = "demo-identity-expired",
            displayName = "Identity",
            issuerLine = "Issued by Veriff  \u00B7  Premium tier  \u00B7  Expired",
            freshnessLabel = "Expired",
            isRevoked = false,
            cachetType = CachetType.IDENTITY,
            trustStatus = TrustStatus.PENDING,
            predicates = listOf("Age 18+", "ID Verified", "Liveness", "Nationality"),
            sharesSummary = "Expired on Mar 1, 2026"
        ),
        CredentialCardUi(
            localId = "demo-childcare",
            displayName = "Childcare",
            issuerLine = "Issued by Parents Association  \u00B7  Standard tier",
            freshnessLabel = "45d",
            isRevoked = false,
            cachetType = CachetType.CHILDCARE,
            trustStatus = TrustStatus.VERIFIED,
            predicates = listOf("Criminal clear", "ID Verified"),
            sharesSummary = "Shared 1 time  \u00B7  Last used 5 days ago"
        )
    )

    override val vaultSummary = VaultSummaryUi(totalCount = 2, verifiedCount = 1, pendingCount = 1)

    override val cachPacks = HappyPathScenario.cachPacks

    override val historyGroups = listOf(
        HistoryGroup(
            dateLabel = "MAR 28",
            entries = listOf(
                HistoryEntry("h-exp1", "Identity check", "Freelance platform verified you", "11:20 AM", "2 proofs shared", VerificationDirection.RECEIVED, TrustStatus.PASSED)
            )
        )
    )

    override val receipts = listOf(
        ReceiptItem("r-exp1", "Identity check", "Freelance platform onboarding", "Mar 10, 2026", 2, ReceiptLogStatus.LOGGED, "Expires Jun 8")
    )

    override val cachetDetails = mapOf(
        "demo-identity-expired" to CachetDetailUi(
            localId = "demo-identity-expired",
            displayName = "Identity",
            cachetType = CachetType.IDENTITY,
            trustStatus = TrustStatus.PENDING,
            issuedDate = "Sep 1, 2025",
            expiresDate = "Mar 1, 2026",
            issuer = "Veriff",
            predicates = listOf(
                RequestPredicate("Age 18 or older", "Your exact age is never shared"),
                RequestPredicate("Identity verified", "Your name is never shared"),
                RequestPredicate("Liveness confirmed", "Biometric data never leaves your device"),
                RequestPredicate("Nationality confirmed", "Only country, not passport number")
            ),
            relatedActivity = listOf(
                HistoryEntry("rd-exp1", "Freelance platform", "2 proofs shared \u00B7 \u2713 Logged", "Mar 28", "", VerificationDirection.RECEIVED, TrustStatus.PASSED)
            )
        ),
        "demo-childcare" to HappyPathScenario.cachetDetails["demo-childcare"]!!
    )
}
