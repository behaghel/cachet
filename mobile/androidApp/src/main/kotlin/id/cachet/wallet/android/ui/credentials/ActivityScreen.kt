package id.cachet.wallet.android.ui.credentials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.components.*
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.android.ui.theme.*

private enum class ActivityFilter { ALL, EXCHANGES, RECEIPTS, CACHETS }

private fun ActivityFilter.label() = when (this) {
    ActivityFilter.ALL -> "All"
    ActivityFilter.EXCHANGES -> "Given"
    ActivityFilter.RECEIPTS -> "Received"
    ActivityFilter.CACHETS -> "Cachets"
}

@Composable
fun ActivityScreen(
    historyGroups: List<HistoryGroup>,
    receipts: List<ReceiptItem> = emptyList(),
    auditResult: String? = null,
    onRunAudit: () -> Unit = {},
    onStartVerification: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onInPersonVerify: () -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(ActivityFilter.ALL) }

    val filteredGroups = remember(selectedFilter, historyGroups) {
        when (selectedFilter) {
            ActivityFilter.CACHETS -> historyGroups.map { group ->
                group.copy(entries = group.entries.filter { it.cachetEarned != null })
            }.filter { it.entries.isNotEmpty() }
            ActivityFilter.EXCHANGES -> historyGroups.map { group ->
                group.copy(entries = group.entries.filter {
                    it.direction == VerificationDirection.GIVEN && it.cachetEarned == null
                })
            }.filter { it.entries.isNotEmpty() }
            ActivityFilter.RECEIPTS -> historyGroups.map { group ->
                group.copy(entries = group.entries.filter {
                    it.direction == VerificationDirection.RECEIVED && it.cachetEarned == null
                })
            }.filter { it.entries.isNotEmpty() }
            else -> historyGroups
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // -- Filter pills --
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

        // -- Content --
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            when (selectedFilter) {
                ActivityFilter.ALL -> {
                    // Unified chronological feed — no section headers
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
                    filteredGroups.forEach { group ->
                        item(key = "date-${group.dateLabel}") {
                            DateLabel(group.dateLabel)
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            HistoryEntryCard(entry = entry)
                        }
                    }
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scan QR (holder flow)
            SmallFloatingActionButton(
                onClick = onScanQr,
                modifier = Modifier.testTag("fab_scan_qr"),
                containerColor = BrandPrimary,
                contentColor = TextOnBrand,
                shape = CircleShape
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
            }
            // In-person verify (proximity)
            SmallFloatingActionButton(
                onClick = onInPersonVerify,
                modifier = Modifier.testTag("fab_in_person"),
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.NearMe, contentDescription = "In-person verification")
            }
            // New request (verifier flow)
            FloatingActionButton(
                onClick = onStartVerification,
                modifier = Modifier.testTag("fab_new_request"),
                containerColor = BrandAccent,
                contentColor = TextOnBrand,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "New verification request")
            }
        }
    }
}

// ===============================================
// SHARED COMPOSABLES
// ===============================================

@Composable
private fun DateLabel(label: String) {
    SectionHeader(text = label)
}

// ===============================================
// HISTORY CARDS
// ===============================================

@Composable
private fun HistoryEntryCard(entry: HistoryEntry) {
    val isDeclined = entry.direction == VerificationDirection.DECLINED

    Card(
        modifier = Modifier.fillMaxWidth().testTag("activity_entry"),
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
