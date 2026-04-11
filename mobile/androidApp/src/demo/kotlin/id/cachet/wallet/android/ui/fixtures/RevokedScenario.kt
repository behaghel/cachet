package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection
import id.cachet.wallet.android.ui.model.*

/** Vault with one revoked credential — exercises revocation UI in the detail screen. */
object RevokedScenario : DemoScenario {
    override val name = "revoked"

    override val credentials = listOf(
        CredentialCardUi(
            localId = "demo-identity-revoked",
            displayName = "Identity",
            issuerLine = "Issued by Veriff  \u00B7  Premium tier  \u00B7  Revoked",
            freshnessLabel = "12d",
            isRevoked = true,
            cachetType = CachetType.IDENTITY,
            trustStatus = TrustStatus.REVOKED,
            predicates = listOf("Age 18+", "ID Verified", "Liveness", "Nationality"),
            sharesSummary = "Revoked on Apr 5, 2026"
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

    override val vaultSummary = VaultSummaryUi(totalCount = 2, verifiedCount = 1, pendingCount = 0, revokedCount = 1)

    override val cachPacks = HappyPathScenario.cachPacks

    override val historyGroups = listOf(
        HistoryGroup(
            dateLabel = "APR 5",
            entries = listOf(
                HistoryEntry("h-rev1", "Identity cachet revoked", "You revoked this cachet", "2:30 PM", "", VerificationDirection.DECLINED, TrustStatus.INCOMPLETE)
            )
        ),
        HistoryGroup(
            dateLabel = "MAR 28",
            entries = listOf(
                HistoryEntry("h-rev2", "Childcare readiness check", "Parents Association verified you", "11:00 AM", "3 proofs shared", VerificationDirection.RECEIVED, TrustStatus.PASSED)
            )
        )
    )

    override val receipts = listOf(
        ReceiptItem("r-rev1", "Childcare readiness check", "Parents Association Madrid", "Mar 28, 2026", 3, ReceiptLogStatus.LOGGED, "Expires Jun 26")
    )

    override val cachetDetails = mapOf(
        "demo-identity-revoked" to CachetDetailUi(
            localId = "demo-identity-revoked",
            displayName = "Identity",
            cachetType = CachetType.IDENTITY,
            trustStatus = TrustStatus.REVOKED,
            issuedDate = "Mar 24, 2026",
            expiresDate = "Dec 24, 2026",
            issuer = "Veriff",
            predicates = listOf(
                RequestPredicate("Age 18 or older", "Your exact age is never shared"),
                RequestPredicate("Identity verified", "Your name is never shared"),
                RequestPredicate("Liveness confirmed", "Biometric data never leaves your device"),
                RequestPredicate("Nationality confirmed", "Only country, not passport number")
            ),
            isRevoked = true,
            revokedDate = "Apr 5, 2026",
            relatedActivity = listOf(
                HistoryEntry("rd-rev1", "Cachet revoked", "You revoked this cachet", "Apr 5", "", VerificationDirection.DECLINED, TrustStatus.INCOMPLETE)
            )
        ),
        "demo-childcare" to HappyPathScenario.cachetDetails["demo-childcare"]!!
    )
}
