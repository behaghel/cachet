package id.cachet.wallet.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.components.*
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.model.*
import id.cachet.wallet.android.ui.theme.*

@Composable
fun HistoryScreen(
    groups: List<HistoryGroup> = DemoFixtures.historyGroups
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }

    val filtered = remember(selectedFilter, groups) {
        if (selectedFilter == HistoryFilter.ALL) groups
        else groups.map { group ->
            group.copy(entries = group.entries.filter { entry ->
                when (selectedFilter) {
                    HistoryFilter.GIVEN -> entry.direction == VerificationDirection.GIVEN
                    HistoryFilter.RECEIVED -> entry.direction == VerificationDirection.RECEIVED
                    HistoryFilter.BADGES -> entry.badgeEarned != null
                    else -> true
                }
            })
        }.filter { it.entries.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Header ──
        Text(
            text = "History",
            style = MaterialTheme.typography.displaySmall
        )
        Text(
            text = "Verifications given and received",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Filter pills ──
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryFilter.entries) { filter ->
                FilterPill(
                    label = filter.label(),
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Timeline ──
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            filtered.forEach { group ->
                item {
                    Text(
                        text = group.dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(group.entries, key = { it.id }) { entry ->
                    if (entry.badgeEarned != null) {
                        BadgeEarnedCard(entry = entry)
                    } else {
                        HistoryEntryCard(entry = entry)
                    }
                }
            }
        }
    }
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

@Composable
private fun HistoryEntryCard(entry: HistoryEntry) {
    val isDeclined = entry.direction == VerificationDirection.DECLINED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
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

private fun HistoryFilter.label() = when (this) {
    HistoryFilter.ALL -> "All"
    HistoryFilter.GIVEN -> "Given"
    HistoryFilter.RECEIVED -> "Received"
    HistoryFilter.BADGES -> "Badges"
}

@Composable
private fun BadgeEarnedCard(entry: HistoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CachetBadgeMark(type = entry.badgeEarned!!, size = 48.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BADGE EARNED",
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
