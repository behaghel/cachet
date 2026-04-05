package id.cachet.wallet.android.ui.model

import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection

enum class HistoryFilter { ALL, GIVEN, RECEIVED, CACHETS }

data class HistoryEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val time: String,
    val proofSummary: String,
    val direction: VerificationDirection,
    val status: TrustStatus,
    val cachetEarned: CachetType? = null
)

data class HistoryGroup(
    val dateLabel: String,
    val entries: List<HistoryEntry>
)
