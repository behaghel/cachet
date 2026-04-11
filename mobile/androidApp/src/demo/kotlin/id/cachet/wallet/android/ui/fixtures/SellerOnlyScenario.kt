package id.cachet.wallet.android.ui.fixtures

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.model.*

/** Single seller credential in PENDING state — forces the seller-fail verification path. */
object SellerOnlyScenario : DemoScenario {
    override val name = "seller-only"

    override val credentials = listOf(
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
        )
    )

    override val vaultSummary = VaultSummaryUi(totalCount = 1, verifiedCount = 0, pendingCount = 1)

    override val cachPacks = listOf(
        CachPackUi(id = PackIds.SAFE_SELLER, question = "Trusted seller?", description = "Identity, platform history, fulfilment rate", proofCount = 4, cachetType = CachetType.SELLER)
    )

    override val historyGroups = emptyList<HistoryGroup>()
    override val receipts = emptyList<ReceiptItem>()

    override val cachetDetails = mapOf(
        "demo-seller" to HappyPathScenario.cachetDetails["demo-seller"]!!
    )
}
