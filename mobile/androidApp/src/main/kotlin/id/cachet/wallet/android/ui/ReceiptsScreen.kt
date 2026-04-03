package id.cachet.wallet.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.fixtures.DemoFixtures
import id.cachet.wallet.android.ui.model.ReceiptItem
import id.cachet.wallet.android.ui.model.ReceiptLogStatus
import id.cachet.wallet.android.ui.theme.*

@Composable
fun ReceiptsScreen(
    receipts: List<ReceiptItem> = DemoFixtures.receipts
) {
    var auditRan by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Header row ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Receipts",
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = "Your data sharing history, on the record",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Button(
                onClick = { auditRan = true },
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

        Spacer(modifier = Modifier.height(16.dp))

        // ── Receipt list + audit bar ──
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(receipts, key = { it.id }) { receipt ->
                ReceiptCard(receipt = receipt)
            }

            // Audit summary bar
            item {
                Spacer(modifier = Modifier.height(4.dp))
                AuditSummaryBar(hasRun = auditRan)
            }
        }
    }
}

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
                // Log status badge
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
private fun AuditSummaryBar(hasRun: Boolean) {
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
                text = "✓  Last audit: 100% of receipts verified in log",
                style = MaterialTheme.typography.bodySmall,
                color = BrandAccentLight
            )
            Text(
                text = if (hasRun) "Just now" else "2d ago",
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
