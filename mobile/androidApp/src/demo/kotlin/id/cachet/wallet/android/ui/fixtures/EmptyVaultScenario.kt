package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.model.*

/** First-time user: no credentials, no history. */
object EmptyVaultScenario : DemoScenario {
    override val name = "empty"
    override val credentials = emptyList<CredentialCardUi>()
    override val vaultSummary = VaultSummaryUi(totalCount = 0, verifiedCount = 0, pendingCount = 0)
    override val cachPacks = HappyPathScenario.cachPacks
    override val historyGroups = emptyList<HistoryGroup>()
    override val receipts = emptyList<ReceiptItem>()
    override val cachetDetails = emptyMap<String, CachetDetailUi>()
    override val defaultScanPack = CachPackUi(
        id = PackIds.CHILDCARE_ES,
        question = "Safe for my kids?",
        description = "Identity, background check, references",
        proofCount = 4,
        cachetType = CachetType.CHILDCARE
    )
}
