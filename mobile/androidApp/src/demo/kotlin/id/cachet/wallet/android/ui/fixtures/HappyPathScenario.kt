package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.trusttrail.model.BehavioralCachetDetailUi
import id.cachet.wallet.android.trusttrail.model.PlatformContributionUi
import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.trusttrail.strength.Tier

/**
 * The default demo scenario: 3 credentials (Identity verified, Childcare verified, Seller pending),
 * activity history, receipts, and detail screens matching the approved wireframes.
 */
object HappyPathScenario : DemoScenario {
    override val name = "happy"

    override val credentials = listOf(
        CredentialCardUi(
            localId = "demo-identity",
            displayName = "Identity",
            issuerLine = "Issued by Veriff  \u00B7  Premium tier  \u00B7  Expires Dec 2026",
            freshnessLabel = "12d",
            isRevoked = false,
            cachetType = CachetType.IDENTITY,
            trustStatus = TrustStatus.VERIFIED,
            predicates = listOf("Age 18+", "ID Verified", "Liveness", "Nationality"),
            sharesSummary = "Shared 3 times  \u00B7  Last used 2 days ago"
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
        ),
        CredentialCardUi(
            localId = "demo-seller",
            displayName = "Safe Seller",
            issuerLine = "Issued by Marketplace  \u00B7  Awaiting platform data",
            freshnessLabel = "\u2014",
            isRevoked = false,
            cachetType = CachetType.SELLER,
            trustStatus = TrustStatus.PENDING,
            predicates = listOf("Fulfilment 95%+", "Low chargebacks"),
            sharesSummary = ""
        ),
        CredentialCardUi(
            localId = "demo-trusted-host",
            displayName = "Trusted Host",
            issuerLine = "Issued by Cachet  \u00B7  Silver tier  \u00B7  72% strength",
            freshnessLabel = "3d",
            isRevoked = false,
            cachetType = CachetType.TRUSTED_HOST,
            trustStatus = TrustStatus.VERIFIED,
            predicates = listOf("Hosting track record", "Identity verified"),
            sharesSummary = "Shared 2 times  \u00B7  Last used 3 days ago"
        )
    )

    override val vaultSummary = VaultSummaryUi(totalCount = 4, verifiedCount = 3, pendingCount = 1)

    override val cachPacks = listOf(
        CachPackUi(id = PackIds.CHILDCARE_ES, question = "Safe for my kids?", description = "Identity, background check, references", proofCount = 4, cachetType = CachetType.CHILDCARE),
        CachPackUi(id = PackIds.SAFE_SELLER, question = "Trusted seller?", description = "Identity, platform history, fulfilment rate", proofCount = 4, cachetType = CachetType.SELLER),
        CachPackUi(id = PackIds.CHILDCARE_BASE, question = "Old enough?", description = "Age verification (18+ or 21+)", proofCount = 1, cachetType = CachetType.AGE),
        CachPackUi(id = PackIds.IDENTITY_BASIC, question = "Who are you?", description = "Identity verification, liveness", proofCount = 2, cachetType = CachetType.IDENTITY)
    )

    override val historyGroups = listOf(
        HistoryGroup(
            dateLabel = "TODAY",
            entries = listOf(
                HistoryEntry("h1", "Childcare readiness check", "You verified someone", "10:32 AM", "4 proofs checked", VerificationDirection.GIVEN, TrustStatus.PASSED),
                HistoryEntry("h2", "Age verification", "Festival Entrada verified you", "9:15 AM", "1 proof shared", VerificationDirection.RECEIVED, TrustStatus.PASSED),
                HistoryEntry("hb1", "Childcare Ready", "Valid for 90 days", "10:32 AM", "", VerificationDirection.GIVEN, TrustStatus.PASSED, cachetEarned = CachetType.CHILDCARE)
            )
        ),
        HistoryGroup(
            dateLabel = "MAR 28",
            entries = listOf(
                HistoryEntry("h3", "Trusted seller check", "You verified someone", "2:45 PM", "2 of 4 proofs passed", VerificationDirection.GIVEN, TrustStatus.INCOMPLETE),
                HistoryEntry("h4", "Identity check", "Freelance platform verified you", "11:20 AM", "2 proofs shared", VerificationDirection.RECEIVED, TrustStatus.PASSED)
            )
        ),
        HistoryGroup(
            dateLabel = "MAR 22",
            entries = listOf(
                HistoryEntry("h5", "Background check request \u2014 you declined", "Unknown requester  \u00B7  3:10 PM", "", "", VerificationDirection.DECLINED, TrustStatus.INCOMPLETE)
            )
        )
    )

    override val receipts = listOf(
        ReceiptItem("r1", "Childcare readiness check", "Parents Association Madrid", "Mar 28, 2026", 4, ReceiptLogStatus.LOGGED, "Expires Jun 26"),
        ReceiptItem("r2", "Age verification", "Festival Entrada", "Mar 22, 2026", 1, ReceiptLogStatus.LOGGED, "Expires Jun 20"),
        ReceiptItem("r3", "Trusted seller check", "Marketplace buyer", "Mar 18, 2026", 4, ReceiptLogStatus.PENDING, "Expires Jun 16"),
        ReceiptItem("r4", "Identity check", "Freelance platform onboarding", "Mar 10, 2026", 2, ReceiptLogStatus.LOGGED, "Expires Jun 8")
    )

    override val behavioralCachetDetails = mapOf(
        "demo-trusted-host" to BehavioralCachetDetailUi(
            localId = "demo-trusted-host",
            displayName = "Trusted Host",
            strength = 0.72f,
            tier = Tier.SILVER,
            issuedDate = "Mar 15, 2026",
            issuer = "Cachet",
            foundationStatus = "Identity \u2713",
            predicates = listOf(
                RequestPredicate("Verified hosting track record", "Based on confirmed exchanges, not reviews"),
                RequestPredicate("Identity verified", "Linked to a Gold identity cachet")
            ),
            evidencePlatforms = listOf(
                PlatformContributionUi("HomeExchange", 7, 72),
                PlatformContributionUi("Vinted", 3, 18),
            ),
        )
    )

    override val cachetDetails = mapOf(
        "demo-childcare" to CachetDetailUi(
            localId = "demo-childcare",
            displayName = "Childcare Ready",
            cachetType = CachetType.CHILDCARE,
            trustStatus = TrustStatus.VERIFIED,
            issuedDate = "Mar 15, 2026",
            expiresDate = "Jun 13, 2026",
            issuer = "Veriff",
            predicates = listOf(
                RequestPredicate("Age 18 or older", "Your exact age is never shared"),
                RequestPredicate("Identity verified", "Your name is never shared"),
                RequestPredicate("No criminal record", "Only a clear/not-clear result")
            ),
            relatedActivity = listOf(
                HistoryEntry("rd1", "Parents Association", "All proofs passed \u00B7 \u2713 Logged", "Mar 15", "", VerificationDirection.RECEIVED, TrustStatus.PASSED),
                HistoryEntry("rd2", "After-school club", "All proofs passed \u00B7 \u2713 Logged", "Mar 22", "", VerificationDirection.RECEIVED, TrustStatus.PASSED)
            )
        ),
        "demo-identity" to CachetDetailUi(
            localId = "demo-identity",
            displayName = "Identity",
            cachetType = CachetType.IDENTITY,
            trustStatus = TrustStatus.VERIFIED,
            issuedDate = "Mar 24, 2026",
            expiresDate = "Dec 24, 2026",
            issuer = "Veriff",
            predicates = listOf(
                RequestPredicate("Age 18 or older", "Your exact age is never shared"),
                RequestPredicate("Identity verified", "Your name is never shared"),
                RequestPredicate("Liveness confirmed", "Biometric data never leaves your device"),
                RequestPredicate("Nationality confirmed", "Only country, not passport number")
            ),
            keyAlias = "android-keystore-demo-identity",
            relatedActivity = listOf(
                HistoryEntry("rd3", "Festival Entrada", "1 proof shared \u00B7 \u2713 Logged", "Today", "", VerificationDirection.RECEIVED, TrustStatus.PASSED),
                HistoryEntry("rd4", "Freelance platform", "2 proofs shared \u00B7 \u2713 Logged", "Mar 28", "", VerificationDirection.RECEIVED, TrustStatus.PASSED)
            )
        ),
        "demo-seller" to CachetDetailUi(
            localId = "demo-seller",
            displayName = "Safe Seller",
            cachetType = CachetType.SELLER,
            trustStatus = TrustStatus.PENDING,
            issuedDate = "\u2014",
            expiresDate = "\u2014",
            issuer = "Marketplace",
            predicates = listOf(
                RequestPredicate("Identity verified", "Your name is never shared"),
                RequestPredicate("Platform tenure \u2265 6 months", "Only pass/fail shared"),
                RequestPredicate("Fulfilment rate \u2265 95%", "Only pass/fail shared"),
                RequestPredicate("No unresolved chargebacks", "Only pass/fail shared")
            ),
            relatedActivity = emptyList()
        )
    )
}
