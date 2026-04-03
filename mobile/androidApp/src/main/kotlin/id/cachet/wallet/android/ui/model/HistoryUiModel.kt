package id.cachet.wallet.android.ui.model

import id.cachet.wallet.android.ui.components.BadgeType
import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection

enum class HistoryFilter { ALL, GIVEN, RECEIVED, BADGES }

data class HistoryEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val time: String,
    val proofSummary: String,
    val direction: VerificationDirection,
    val status: TrustStatus,
    val badgeEarned: BadgeType? = null
)

data class HistoryGroup(
    val dateLabel: String,
    val entries: List<HistoryEntry>
)
