package id.cachet.wallet.android.ui.mapper

import id.cachet.wallet.android.ui.components.TrustStatus
import id.cachet.wallet.android.ui.components.VerificationDirection
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.domain.model.ConsentReceipt
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.days

object ActivityMapper {

    fun toReceiptItem(receipt: ConsentReceipt): ReceiptItem {
        val logStatus = if (receipt.transparencyLogEntry?.isVerified == true) {
            ReceiptLogStatus.LOGGED
        } else {
            ReceiptLogStatus.PENDING
        }

        val retentionDays = receipt.userConsent.retentionPeriodDays
        val expiresAt = receipt.timestamp + retentionDays.days

        return ReceiptItem(
            id = receipt.id,
            title = receipt.purpose,
            counterparty = receipt.rpDisplayName,
            date = formatDate(receipt.timestamp),
            predicateCount = receipt.predicatesProven.size,
            logStatus = logStatus,
            expiresLabel = formatExpiryLabel(expiresAt)
        )
    }

    fun toHistoryEntry(receipt: ConsentReceipt): HistoryEntry {
        return HistoryEntry(
            id = receipt.id,
            title = receipt.purpose,
            subtitle = "${receipt.rpDisplayName} verified you",
            time = formatTime(receipt.timestamp),
            proofSummary = "${receipt.predicatesProven.size} proofs shared",
            direction = VerificationDirection.RECEIVED,
            status = TrustStatus.PASSED
        )
    }

    fun groupByDate(
        entries: List<HistoryEntry>,
        timestamps: Map<String, Instant>
    ): List<HistoryGroup> {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date

        return entries
            .sortedByDescending { timestamps[it.id] ?: Clock.System.now() }
            .groupBy { entry ->
                val instant = timestamps[entry.id] ?: Clock.System.now()
                val date = instant.toLocalDateTime(tz).date
                when (date) {
                    today -> "TODAY"
                    today.minus(1, DateTimeUnit.DAY) -> "YESTERDAY"
                    else -> formatDateLabel(date)
                }
            }
            .map { (label, grouped) -> HistoryGroup(dateLabel = label, entries = grouped) }
    }

    // ── Formatters ──

    private val MONTH_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    private val MONTH_NAMES_UPPER = MONTH_NAMES.map { it.uppercase() }

    private fun formatDate(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${MONTH_NAMES[dt.monthNumber - 1]} ${dt.dayOfMonth}, ${dt.year}"
    }

    private fun formatTime(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = when {
            dt.hour == 0 -> 12
            dt.hour > 12 -> dt.hour - 12
            else -> dt.hour
        }
        val amPm = if (dt.hour < 12) "AM" else "PM"
        return "$hour:${dt.minute.toString().padStart(2, '0')} $amPm"
    }

    private fun formatExpiryLabel(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "Expires ${MONTH_NAMES[dt.monthNumber - 1]} ${dt.dayOfMonth}"
    }

    private fun formatDateLabel(date: LocalDate): String {
        return "${MONTH_NAMES_UPPER[date.monthNumber - 1]} ${date.dayOfMonth}"
    }
}
