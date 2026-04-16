package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.domain.model.CredentialSubject
import id.cachet.wallet.domain.model.PersonalData
import id.cachet.wallet.domain.model.VerifiableCredential

/**
 * Single source of truth for deterministic QA state.
 * Used by demo mode and the /android-ux-review skill.
 *
 * Delegates to [activeScenario] so that different demo scenarios
 * (revoked, expired, seller-only, etc.) can be selected at launch via
 * `--es demo_scenario <name>`.
 */
object DemoFixtures {

    /** Flag for BDD tests to activate demo mode without intent extras. */
    @Volatile
    var isDemoActive: Boolean = false

    /** The active scenario. Set once during app init before any composable reads it. */
    var activeScenario: DemoScenario = HappyPathScenario

    /** Override which pack the QR scanner demo uses. null = use scenario default. */
    @Volatile
    var overrideScanPack: CachPackUi? = null

    /** Liveness result for demo mode. Set by BDD tests before triggering liveness. */
    enum class LivenessResult { PASS, FAIL, NONE }
    @Volatile
    var livenessResult: LivenessResult = LivenessResult.NONE

    /** Synthetic credential for consent receipt generation when no real credential is in the repo. */
    val syntheticCredential = VerifiableCredential(
        id = "urn:demo:synthetic",
        context = listOf("https://www.w3.org/2018/credentials/v1"),
        type = listOf("VerifiableCredential", "IdentityCredential"),
        issuer = "did:web:demo.cachet.id",
        issuanceDate = "2026-04-01T10:00:00Z",
        credentialSubject = CredentialSubject(
            id = "did:key:demo-holder",
            verified = true,
            personalData = PersonalData(age = 30, nationality = "FR", documentType = "passport"),
            verificationLevel = "premium"
        )
    )

    // -- Delegated properties: route through active scenario --

    val credentials: List<CredentialCardUi> get() = activeScenario.credentials
    val vaultSummary: VaultSummaryUi get() = activeScenario.vaultSummary
    val cachPacks: List<CachPackUi> get() = activeScenario.cachPacks
    val historyGroups: List<HistoryGroup> get() = activeScenario.historyGroups
    val receipts: List<ReceiptItem> get() = activeScenario.receipts
    val cachetDetails: Map<String, CachetDetailUi> get() = activeScenario.cachetDetails

    fun detailFor(localId: String): CachetDetailUi? = activeScenario.cachetDetails[localId]

    /** Look up the demo pack matching a CachetType. */
    fun packForType(type: CachetType): CachPackUi =
        activeScenario.cachPacks.firstOrNull { it.cachetType == type }
            ?: activeScenario.cachPacks.first()

    /** Delegate pass/fail decision to the active scenario. */
    fun shouldPass(request: VerificationRequest): Boolean =
        activeScenario.shouldPass(request)

    /** The pack to use for the next QR demo scan (override or scenario default). */
    val effectiveScanPack: CachPackUi
        get() = overrideScanPack ?: activeScenario.defaultScanPack

    /** Per-CachPack liveness policy: high-value packs require liveness, low-value skip. */
    fun requiresLiveness(type: CachetType): Boolean = when (type) {
        CachetType.CHILDCARE -> true
        CachetType.SELLER -> true
        CachetType.IDENTITY -> true
        CachetType.AGE -> false
    }

    // -- Static overlay fixtures (not scenario-dependent) --

    val qrShareState = QrShareState(
        question = "Safe for my kids?",
        predicates = listOf("Age 18+", "ID Verified", "No record", "2+ refs"),
        expiresLabel = "Request expires in 4:58"
    )

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

    val ageRequest = VerificationRequest(
        question = "Are you old enough?",
        predicates = listOf(
            RequestPredicate("You are 18 or older", "Your exact age will NOT be shared")
        ),
        retentionDays = 30,
        loggedInTransparencyLog = true
    )

}
