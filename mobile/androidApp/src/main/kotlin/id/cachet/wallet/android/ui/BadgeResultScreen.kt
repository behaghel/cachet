package id.cachet.wallet.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.BadgeType
import id.cachet.wallet.android.ui.components.CachetBadgeMark
import id.cachet.wallet.android.ui.components.SealButton
import id.cachet.wallet.android.ui.model.BadgeResult
import id.cachet.wallet.android.ui.model.PredicateResult
import id.cachet.wallet.android.ui.theme.*

@Composable
fun BadgeResultScreen(
    result: BadgeResult,
    onDone: () -> Unit,
    onViewReceipt: (() -> Unit)? = null
) {
    val accentColor = if (result.allPassed) BrandAccent else BrandWarm

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BrandPrimaryDark
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Close button ──
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TrustNeutral
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Shield badge with glow ──
            item {
                Box(contentAlignment = Alignment.Center) {
                    // Glow rings
                    Canvas(modifier = Modifier.size(220.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        drawCircle(
                            color = accentColor.copy(alpha = 0.15f),
                            radius = size.minDimension / 2,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = accentColor.copy(alpha = 0.25f),
                            radius = size.minDimension / 2 * 0.86f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    // Shield badge mark
                    CachetBadgeMark(
                        type = result.badgeType,
                        size = 180.dp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Badge name + summary pill ──
            item {
                Text(
                    text = result.badgeName,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (result.allPassed) BrandAccentDark else Color(0xFF4C1D1D)
                ) {
                    Text(
                        text = if (result.allPassed) "All ${result.totalCount} proofs passed"
                               else "${result.passedCount} of ${result.totalCount} proofs passed",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (result.allPassed) BrandAccentLight else TrustRevokedBorder
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Verified just now",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrustNeutral,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Predicate results card ──
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = BrandPrimary
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        result.predicates.forEachIndexed { index, predicate ->
                            PredicateResultRow(predicate)
                            if (index < result.predicates.lastIndex) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // Metadata footer
                        if (result.validityLabel != null || !result.allPassed) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = SurfaceBorderDark, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (result.allPassed) "Badge valid for" else "No badge issued",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrustNeutral
                                )
                                Text(
                                    text = result.validityLabel ?: "All proofs required",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Consent receipt bar ──
            item {
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
                            text = "✓  Consent receipt logged",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandAccentLight
                        )
                        if (onViewReceipt != null) {
                            TextButton(onClick = onViewReceipt) {
                                Text(
                                    text = "View",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrustNeutral
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Done button ──
            item {
                SealButton(
                    text = "Done",
                    onClick = onDone,
                    containerColor = Color.White,
                    contentColor = BrandPrimary
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PredicateResultRow(predicate: PredicateResult) {
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = if (predicate.passed) "✓" else "✗",
                style = MaterialTheme.typography.bodyLarge,
                color = if (predicate.passed) BrandAccent else BrandWarm,
                modifier = Modifier.width(24.dp)
            )
            Column {
                Text(
                    text = predicate.label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = if (predicate.passed) Color.White else BrandWarm
                )
                if (!predicate.passed && predicate.failReason != null) {
                    Text(
                        text = predicate.failReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = TrustNeutral
                    )
                }
            }
        }
    }
}
