package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.model.*

/**
 * A scenario is a complete, self-consistent demo state.
 * Each scenario packages all the data needed for one demo walkthrough:
 * credentials, packs, history, receipts, details, and verification logic.
 *
 * Selectable at launch via: `--es demo_scenario <name>`
 */
interface DemoScenario {
    val name: String
    val credentials: List<CredentialCardUi>
    val vaultSummary: VaultSummaryUi
    val cachPacks: List<CachPackUi>
    val historyGroups: List<HistoryGroup>
    val receipts: List<ReceiptItem>
    val cachetDetails: Map<String, CachetDetailUi>
    /** Which pack to use for QR scanner demo fallback. */
    val defaultScanPack: CachPackUi get() = cachPacks.first()
    /** Override the pass/fail decision for shareCredential demo. Default: seller fails, all others pass. */
    fun shouldPass(request: VerificationRequest): Boolean =
        !request.question.contains("seller", ignoreCase = true)
}
