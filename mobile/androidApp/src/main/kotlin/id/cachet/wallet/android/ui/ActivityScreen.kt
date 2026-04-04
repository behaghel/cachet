package id.cachet.wallet.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.components.*
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.android.ui.theme.*

private enum class ActivityFilter { ALL, EXCHANGES, RECEIPTS, CACHETS }

private fun ActivityFilter.label() = when (this) {
    ActivityFilter.ALL -> "All"
    ActivityFilter.EXCHANGES -> "Exchanges"
    ActivityFilter.RECEIPTS -> "Receipts"
    ActivityFilter.CACHETS -> "Cachets"
}

@Composable
fun ActivityScreen(
    historyGroups: List<HistoryGroup>,
    receipts: List<ReceiptItem>,
    auditResult: String? = null,
    onRunAudit: () -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }

    val filteredGroups = remember(selectedFilter, historyGroups) {
        when (selectedFilter) {
            ActivityFilter.CACHETS -> historyGroups.map { group ->
                group.copy(entries = group.entries.filter { it.cachetEarned != null })
            }.filter { it.entries.isNotEmpty() }
            ActivityFilter.EXCHANGES -> historyGroups.map { group ->
                group.copy(entries = group.entries.filter { it.cachetEarned == null })
            }.filter { it.entries.isNotEmpty() }
            else -> historyGroups
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Filter pills ──
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ActivityFilter.entries) { filter ->
                FilterPill(
                    label = filter.label(),
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Content ──
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            when (selectedFilter) {
                ActivityFilter.ALL -> {
                    // History section
                    item {
                        SectionHeader("Recent exchanges")
                    }
                    filteredGroups.forEach { group ->
                        item(key = "date-${group.dateLabel}") {
                            DateLabel(group.dateLabel)
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            if (entry.cachetEarned != null) {
                                CachetEarnedCard(entry = entry)
                            } else {
                                HistoryEntryCard(entry = entry)
                            }
                        }
                    }

                    // Receipts section
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader("Receipts")
                    }
                    items(receipts, key = { it.id }) { receipt ->
                        ReceiptCard(receipt = receipt)
                    }

                    item { AuditSummaryBar(result = auditResult) }
                }

                ActivityFilter.EXCHANGES -> {
                    filteredGroups.forEach { group ->
                        item(key = "date-${group.dateLabel}") {
                            DateLabel(group.dateLabel)
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            HistoryEntryCard(entry = entry)
                        }
                    }
                }

                ActivityFilter.RECEIPTS -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your data sharing history, on the record",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onRunAudit,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandPrimary,
                                    contentColor = TextOnBrand
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Run Audit",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    items(receipts, key = { it.id }) { receipt ->
                        ReceiptCard(receipt = receipt)
                    }
                    item { AuditSummaryBar(result = auditResult) }
                }

                ActivityFilter.CACHETS -> {
                    filteredGroups.forEach { group ->
                        item(key = "date-${group.dateLabel}") {
                            DateLabel(group.dateLabel)
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            CachetEarnedCard(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// SHARED COMPOSABLES
// ═══════════════════════════════════════════

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = TextTertiary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun DateLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = TextTertiary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) BrandPrimary else SurfaceElevated,
        animationSpec = tween(200), label = "pill-bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) TextOnBrand else TextSecondary,
        animationSpec = tween(200), label = "pill-fg"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = bg
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg
        )
    }
}

// ═══════════════════════════════════════════
// HISTORY CARDS
// ═══════════════════════════════════════════

@Composable
private fun HistoryEntryCard(entry: HistoryEntry) {
    val isDeclined = entry.direction == VerificationDirection.DECLINED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DirectionIndicator(direction = entry.direction)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDeclined) TextTertiary else TextPrimary
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDeclined) TextTertiary else TextSecondary
                )
                Text(
                    text = "${entry.time}  ·  ${entry.proofSummary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            if (!isDeclined) {
                Column(horizontalAlignment = Alignment.End) {
                    TrustStatusChip(status = entry.status)
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CachetEarnedCard(entry: HistoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CachetMark(type = entry.cachetEarned!!, size = 48.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CACHET EARNED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TrustVerifiedText
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            TrustStatusChip(status = TrustStatus.PASSED)
        }
    }
}

// ═══════════════════════════════════════════
// RECEIPT CARDS
// ═══════════════════════════════════════════

@Composable
private fun ReceiptCard(receipt: ReceiptItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Status dot
            val dotColor = when (receipt.logStatus) {
                ReceiptLogStatus.LOGGED -> TrustVerified
                ReceiptLogStatus.PENDING -> TrustPending
            }
            Surface(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = dotColor
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receipt.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = receipt.counterparty,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "${receipt.date}  ·  ${receipt.predicateCount} predicates shared",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val (badgeLabel, badgeBg, badgeBorder, badgeFg) = when (receipt.logStatus) {
                    ReceiptLogStatus.LOGGED -> LogBadgeColors("Logged", TrustVerifiedBg, TrustVerifiedBorder, TrustVerifiedText)
                    ReceiptLogStatus.PENDING -> LogBadgeColors("Pending", TrustPendingBg, TrustPendingBorder, TrustPendingText)
                }
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = badgeBg,
                    border = BorderStroke(1.dp, badgeBorder)
                ) {
                    Text(
                        text = badgeLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeFg
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = receipt.expiresLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun AuditSummaryBar(result: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceAccentTintDark
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (result != null) "✓  Last audit: $result" else "✓  No audit run yet",
                style = MaterialTheme.typography.bodySmall,
                color = BrandAccentLight
            )
            Text(
                text = if (result != null) "Just now" else "—",
                style = MaterialTheme.typography.labelSmall,
                color = TrustNeutral
            )
        }
    }
}

private data class LogBadgeColors(
    val label: String,
    val bg: Color,
    val border: Color,
    val text: Color
)
