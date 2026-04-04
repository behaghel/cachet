package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.components.CachetType
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
            displayName = "Identity",
            issuerLine = "Issued by Veriff  ·  Premium tier  ·  Expires Dec 2026",
            freshnessLabel = "12d",
            isRevoked = false,
            cachetType = CachetType.IDENTITY,
            trustStatus = TrustStatus.VERIFIED,
            predicates = listOf("Age 18+", "ID Verified", "Liveness", "Nationality"),
            sharesSummary = "Shared 3 times  ·  Last used 2 days ago"
        ),
        CredentialCardUi(
            localId = "demo-childcare",
            displayName = "Childcare",
            issuerLine = "Issued by Parents Association  ·  Standard tier",
            freshnessLabel = "45d",
            isRevoked = false,
            cachetType = CachetType.CHILDCARE,
            trustStatus = TrustStatus.VERIFIED,
            predicates = listOf("Criminal clear", "ID Verified"),
            sharesSummary = "Shared 1 time  ·  Last used 5 days ago"
        ),
        CredentialCardUi(
            localId = "demo-seller",
            displayName = "Safe Seller",
            issuerLine = "Issued by Marketplace  ·  Awaiting platform data",
            freshnessLabel = "—",
            isRevoked = false,
            cachetType = CachetType.SELLER,
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

    // ── Home / Cache it section ── (inline on My Cachets tab)

    val cachPacks: List<CachPackUi> = listOf(
        CachPackUi("Safe for my kids?", "Identity, background check, references", 4, CachetType.CHILDCARE),
        CachPackUi("Trusted seller?", "Identity, platform history, fulfilment rate", 4, CachetType.SELLER),
        CachPackUi("Old enough?", "Age verification (18+ or 21+)", 1, CachetType.AGE)
    )

    // ── Activity ── (wireframe: activity-01-tab)

    val historyGroups: List<HistoryGroup> = listOf(
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
                HistoryEntry("h5", "Background check request — you declined", "Unknown requester  ·  3:10 PM", "", "", VerificationDirection.DECLINED, TrustStatus.INCOMPLETE)
            )
        )
    )

    // ── Receipts ── (shown in Activity tab, Receipts filter)

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

    // ── Overlay: Seller Request ──

    val sellerRequest = VerificationRequest(
        question = "Are you a trusted seller?",
        predicates = listOf(
            RequestPredicate("Your identity is verified", "Your name will NOT be shared"),
            RequestPredicate("Platform history available", "Only summary metrics shared"),
            RequestPredicate("Fulfilment rate above 95%", "Only a pass/fail result"),
            RequestPredicate("Low chargeback rate", "Only a pass/fail result")
        ),
        retentionDays = 90,
        loggedInTransparencyLog = true
    )

    // ── Overlay: Age Request ──

    val ageRequest = VerificationRequest(
        question = "Are you old enough?",
        predicates = listOf(
            RequestPredicate("You are 18 or older", "Your exact age will NOT be shared")
        ),
        retentionDays = 30,
        loggedInTransparencyLog = true
    )

    // ── Overlay: Cachet Result Pass ── (wireframe: cachet-04-result-pass)

    val cachetResultPass = CachetResult(
        cachetName = "Childcare Ready",
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
        cachetType = CachetType.CHILDCARE
    )

    // ── Overlay: Cachet Result Fail ── (wireframe: cachet-05-result-fail)

    val cachetResultFail = CachetResult(
        cachetName = "Incomplete",
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
        cachetType = CachetType.CHILDCARE
    )
}
