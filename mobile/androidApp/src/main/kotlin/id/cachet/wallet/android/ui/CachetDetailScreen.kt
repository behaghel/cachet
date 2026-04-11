package id.cachet.wallet.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.*
import id.cachet.wallet.android.ui.model.CachetDetailUi
import id.cachet.wallet.android.ui.model.HistoryEntry
import id.cachet.wallet.android.ui.model.RequestPredicate
import id.cachet.wallet.android.ui.theme.*

@Composable
fun CachetDetailScreen(
    detail: CachetDetailUi,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRevoke: () -> Unit,
    onSeeAllActivity: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceBackground
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // ── Back button ──
            item {
                Spacer(modifier = Modifier.height(48.dp))
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            }

            // ── Hero: shield + name + status ──
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CachetMark(type = detail.cachetType, size = 80.dp)
                        if (detail.isRevoked) {
                            val forbiddenRed = Color(0xFFB91C1C)
                            Canvas(modifier = Modifier.size(80.dp)) {
                                val c = Offset(size.width / 2, size.height / 2)
                                val r = size.minDimension / 2 * 0.75f
                                val sw = 5.dp.toPx()
                                drawCircle(color = forbiddenRed, radius = r, center = c, style = Stroke(width = sw))
                                val offset = r * 0.707f
                                drawLine(
                                    color = forbiddenRed,
                                    start = Offset(c.x - offset, c.y - offset),
                                    end = Offset(c.x + offset, c.y + offset),
                                    strokeWidth = sw,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = detail.displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TrustStatusChip(status = detail.trustStatus)
                    Spacer(modifier = Modifier.height(12.dp))
                    SealButton(
                        text = "Share",
                        onClick = onShare,
                        enabled = !detail.isRevoked
                    )
                    if (detail.isRevoked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Revoked credentials cannot be shared",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandWarm,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── Metadata rows ──
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        MetadataField(label = "Issued", value = detail.issuedDate)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (detail.isRevoked && detail.revokedDate != null) {
                            MetadataField(label = "Revoked", value = detail.revokedDate, valueColor = BrandWarm)
                        } else {
                            MetadataField(label = "Expires", value = detail.expiresDate)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                MetadataField(label = "Issuer", value = detail.issuer)
                // ── Hardware-backed indicator (#62) ──
                if (detail.keyAlias != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceElevated)
                    Spacer(modifier = Modifier.height(12.dp))
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
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── "What this proves" ──
            item {
                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "What this proves",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Predicates card ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        detail.predicates.forEachIndexed { index, predicate ->
                            DetailPredicateRow(predicate)
                            if (index < detail.predicates.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = SurfaceElevated,
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // ── "Related activity" ──
            if (detail.relatedActivity.isNotEmpty()) {
                item {
                    HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Related activity",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "See all",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandAccent,
                            modifier = Modifier.clickable(onClick = onSeeAllActivity)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                itemsIndexed(detail.relatedActivity) { _, entry ->
                    RelatedActivityCard(entry)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ── Revoke link (hidden when already revoked) ──
            if (!detail.isRevoked) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Revoke this cachet",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandWarm,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onRevoke)
                            .padding(vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun MetadataField(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun DetailPredicateRow(predicate: RequestPredicate) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "\u2713",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandAccent,
            modifier = Modifier.width(24.dp)
        )
        Column {
            Text(
                text = predicate.claim,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = TextPrimary
            )
            Text(
                text = predicate.privacyNote,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun RelatedActivityCard(entry: HistoryEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DirectionIndicator(direction = entry.direction)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = "${entry.time} \u00B7 ${entry.subtitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}
