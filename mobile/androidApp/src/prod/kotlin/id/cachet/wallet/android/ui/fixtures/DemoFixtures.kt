package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.domain.model.VerifiableCredential

/**
 * Prod stub: demo fixtures are not available in production builds.
 * All property accesses throw immediately — if this is ever reached,
 * it means demo code leaked into a non-demo code path.
 */
object DemoFixtures {
    var activeScenario: Any
        get() = error("Demo not available in production")
        set(_) = error("Demo not available in production")

    val syntheticCredential: VerifiableCredential get() = error("Demo not available in production")
    val credentials: List<CredentialCardUi> get() = error("Demo not available in production")
    val vaultSummary: VaultSummaryUi get() = error("Demo not available in production")
    val cachPacks: List<CachPackUi> get() = error("Demo not available in production")
    val historyGroups: List<HistoryGroup> get() = error("Demo not available in production")
    val receipts: List<ReceiptItem> get() = error("Demo not available in production")
    val cachetDetails: Map<String, CachetDetailUi> get() = error("Demo not available in production")
    val childcareRequest: VerificationRequest get() = error("Demo not available in production")
    val sellerRequest: VerificationRequest get() = error("Demo not available in production")
    val ageRequest: VerificationRequest get() = error("Demo not available in production")
    val qrShareState: QrShareState get() = error("Demo not available in production")

    fun detailFor(localId: String): CachetDetailUi? = error("Demo not available in production")
    fun packForType(type: CachetType): CachPackUi = error("Demo not available in production")
    fun shouldPass(request: VerificationRequest): Boolean = error("Demo not available in production")
}
