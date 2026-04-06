package id.cachet.wallet.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.*
import id.cachet.wallet.android.ui.model.CredentialCardUi
import id.cachet.wallet.android.ui.model.HistoryEntry
import id.cachet.wallet.android.ui.theme.*

@Composable
fun CachetDetailScreen(
    card: CredentialCardUi,
    relatedActivity: List<HistoryEntry>,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRevoke: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // -- Back button --
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary
                        )
                    }
                }
            }

            // -- Hero shield + title + status --
            item {
                Spacer(modifier = Modifier.height(8.dp))
                if (card.cachetType != null) {
                    CachetMark(type = card.cachetType, size = 96.dp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = card.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TrustStatusChip(status = card.trustStatus)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // -- Metadata card --
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = BorderStroke(1.dp, SurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MetadataRow("Issuer", card.issuerLine.substringBefore("  ·"))
                        MetadataRow("Freshness", card.freshnessLabel)
                        MetadataRow("Shares", card.sharesSummary.ifEmpty { "Not shared yet" })
                        if (card.isRevoked) {
                            MetadataRow("Status", "Revoked", valueColor = TrustRevokedText)
                        }
                        // -- Hardware-backed indicator (#62) --
                        if (card.keyAlias != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = SurfaceElevated)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = "Hardware-secured",
                                    tint = BrandAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Hardware-secured",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandAccent
                                    )
                                    Text(
                                        text = "Bound to your device's secure element",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // -- What this proves --
            item {
                Text(
                    text = "What this proves",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = BorderStroke(1.dp, SurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        card.predicates.forEachIndexed { index, predicate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(24.dp),
                                    shape = CircleShape,
                                    color = TrustVerifiedBg
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "\u2713",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BrandAccent
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = predicate,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (index < card.predicates.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    color = SurfaceElevated
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // -- Related activity --
            if (relatedActivity.isNotEmpty()) {
                item {
                    Text(
                        text = "Related activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(relatedActivity, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DirectionIndicator(direction = entry.direction)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${entry.subtitle}  ·  ${entry.time}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                            }
                            TrustStatusChip(status = entry.status)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            // -- Action buttons --
            item {
                SealButton(
                    text = "Share",
                    onClick = onShare,
                    enabled = !card.isRevoked
                )
                if (card.isRevoked) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Revoked credentials cannot be shared",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrustRevokedText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (!card.isRevoked) {
                    TextButton(
                        onClick = onRevoke,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            "Revoke this credential",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = BrandWarm
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}
