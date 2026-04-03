package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.components.BadgeType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection
import id.cachet.wallet.android.ui.model.*

/**
 * Single source of truth for deterministic QA state.
 * Used by demo mode and the /android-ux-review skill.
 * Data matches the approved wireframes in design/wireframes/.
 */
object DemoFixtures {

    // ── Home / My Trust ── (wireframe: holder-04-vault-my-trust)

    val credentials: List<CredentialCardUi> = listOf(
        CredentialCardUi(
            localId = "demo-identity",
            displayName = "Identity Credential",
            issuerLine = "Issued by Veriff  ·  Premium tier  ·  Expires Dec 2026",
            freshnessLabel = "12d",
            isRevoked = false,
            badgeType = BadgeType.IDENTITY,
            trustStatus = TrustStatus.VERIFIED,
            predicates = listOf("Age 18+", "ID Verified", "Liveness", "Nationality"),
            sharesSummary = "Shared 3 times  ·  Last used 2 days ago"
        ),
        CredentialCardUi(
            localId = "demo-background",
            displayName = "Background Check",
            issuerLine = "Issued by ClearCheck  ·  Standard tier",
            freshnessLabel = "45d",
            isRevoked = false,
            badgeType = BadgeType.CHILDCARE,
            trustStatus = TrustStatus.VERIFIED,
            predicates = listOf("Criminal clear", "ID Verified"),
            sharesSummary = "Shared 1 time  ·  Last used 5 days ago"
        ),
        CredentialCardUi(
            localId = "demo-seller",
            displayName = "Safe Seller Badge",
            issuerLine = "Issued by Marketplace  ·  Awaiting platform data",
            freshnessLabel = "—",
            isRevoked = false,
            badgeType = BadgeType.SELLER,
            trustStatus = TrustStatus.PENDING,
            predicates = listOf("Fulfilment 95%+", "Low chargebacks"),
            sharesSummary = ""
        )
    )

    val vaultSummary = VaultSummaryUi(
        totalCount = 3,
        verifiedCount = 2,
        pendingCount = 1
    )

    // ── Home / Verify tab ── (wireframe: home-c-dual-tabs)

    val trustPacks: List<TrustPackUi> = listOf(
        TrustPackUi("Safe for my kids?", "Identity, background check, references", 4, BadgeType.CHILDCARE),
        TrustPackUi("Trusted seller?", "Identity, platform history, fulfilment rate", 4, BadgeType.SELLER),
        TrustPackUi("Old enough?", "Age verification (18+ or 21+)", 1, BadgeType.AGE)
    )

    // ── History ── (wireframe: activity-01-tab / history-01-tab)

    val historyGroups: List<HistoryGroup> = listOf(
        HistoryGroup(
            dateLabel = "TODAY",
            entries = listOf(
                HistoryEntry("h1", "Childcare readiness check", "You verified someone", "10:32 AM", "4 proofs checked", VerificationDirection.GIVEN, TrustStatus.PASSED),
                HistoryEntry("h2", "Age verification", "Festival Entrada verified you", "9:15 AM", "1 proof shared", VerificationDirection.RECEIVED, TrustStatus.PASSED),
                HistoryEntry("hb1", "Childcare Ready", "Valid for 90 days", "10:32 AM", "", VerificationDirection.GIVEN, TrustStatus.PASSED, badgeEarned = BadgeType.CHILDCARE)
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
                HistoryEntry("h5", "Background check request — you declined", "Unknown requester  ·  3:10 PM", "", "", VerificationDirection.DECLINED, TrustStatus.INCOMPLETE)
            )
        )
    )

    // ── Receipts ── (wireframe: holder-06-receipts)

    val receipts: List<ReceiptItem> = listOf(
        ReceiptItem("r1", "Childcare readiness check", "Parents Association Madrid", "Mar 28, 2026", 4, ReceiptLogStatus.LOGGED, "Expires Jun 26"),
        ReceiptItem("r2", "Age verification", "Festival Entrada", "Mar 22, 2026", 1, ReceiptLogStatus.LOGGED, "Expires Jun 20"),
        ReceiptItem("r3", "Trusted seller check", "Marketplace buyer", "Mar 18, 2026", 4, ReceiptLogStatus.PENDING, "Expires Jun 16"),
        ReceiptItem("r4", "Identity check", "Freelance platform onboarding", "Mar 10, 2026", 2, ReceiptLogStatus.LOGGED, "Expires Jun 8")
    )

    // ── Overlay: QR Share ── (wireframe: verify-02-qr-share)

    val qrShareState = QrShareState(
        question = "Safe for my kids?",
        predicates = listOf("Age 18+", "ID Verified", "No record", "2+ refs"),
        expiresLabel = "Request expires in 4:58"
    )

    // ── Overlay: Incoming Request ── (wireframe: verify-03-incoming-request)

    val childcareRequest = VerificationRequest(
        question = "Are you safe for childcare?",
        predicates = listOf(
            RequestPredicate("You are 18 or older", "Your exact age will NOT be shared"),
            RequestPredicate("Your identity is verified", "Your name will NOT be shared"),
            RequestPredicate("No criminal record", "Only a clear/not-clear result"),
            RequestPredicate("2+ verified references", "Referee names will NOT be shared")
        ),
        retentionDays = 90,
        loggedInTransparencyLog = true
    )

    // ── Overlay: Badge Result Pass ── (wireframe: verify-04-badge-result)

    val badgeResultPass = BadgeResult(
        badgeName = "Childcare Ready",
        allPassed = true,
        passedCount = 4,
        totalCount = 4,
        predicates = listOf(
            PredicateResult("Age 18 or older", true),
            PredicateResult("Identity verified", true),
            PredicateResult("No criminal record", true),
            PredicateResult("2+ verified references", true)
        ),
        validityLabel = "90 days",
        badgeType = BadgeType.CHILDCARE
    )

    // ── Overlay: Badge Result Fail ── (wireframe: verify-05-badge-result-fail)

    val badgeResultFail = BadgeResult(
        badgeName = "Incomplete",
        allPassed = false,
        passedCount = 2,
        totalCount = 4,
        predicates = listOf(
            PredicateResult("Age 18 or older", true),
            PredicateResult("Identity verified", true),
            PredicateResult("No criminal record", false, "Credential not available"),
            PredicateResult("2+ verified references", false, "Only 1 reference on file")
        ),
        validityLabel = null,
        badgeType = BadgeType.CHILDCARE
    )
}
