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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.CachetMark
import id.cachet.wallet.android.ui.components.SealButton
import id.cachet.wallet.android.ui.model.CachetResult
import id.cachet.wallet.android.ui.model.PredicateResult
import id.cachet.wallet.android.ui.theme.*

@Composable
fun CachetResultScreen(
    result: CachetResult,
    onDone: () -> Unit,
    onViewReceipt: (() -> Unit)? = null
) {
    // -- Technical error: distinct from verification failure --
    if (result.isError) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BrandPrimaryDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TrustNeutral)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Something went wrong",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Verification could not be completed due to a technical issue. This is not a failed verification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TrustNeutral,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (result.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandPrimary
                    ) {
                        Text(
                            text = result.errorMessage,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = TrustNeutral
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                SealButton(
                    text = "Try again",
                    onClick = onDone,
                    containerColor = Color.White,
                    contentColor = BrandPrimary
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        return
    }

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
            // -- Close button --
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            // -- Shield cachet with glow --
            item {
                Box(contentAlignment = Alignment.Center) {
                    // Glow rings
                    Canvas(modifier = Modifier.size(160.dp)) {
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
                    // Shield cachet mark
                    CachetMark(
                        type = result.cachetType,
                        size = 120.dp
                    )
                    // Forbidden sign overlay for fail state
                    if (!result.allPassed) {
                        val forbiddenRed = Color(0xFFB91C1C)
                        Canvas(modifier = Modifier.size(120.dp)) {
                            val c = Offset(size.width / 2, size.height / 2)
                            val r = size.minDimension / 2 * 0.85f
                            val sw = 10.dp.toPx()
                            drawCircle(
                                color = forbiddenRed,
                                radius = r,
                                center = c,
                                style = Stroke(width = sw)
                            )
                            // Diagonal line (top-left to bottom-right)
                            val offset = r * 0.707f // cos(45°)
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
            }

            // -- Cachet name + summary pill --
            item {
                Text(
                    text = result.cachetName,
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Verified just now",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrustNeutral,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // -- Predicate results card --
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
                                    text = if (result.allPassed) "Cachet valid for" else "No cachet issued",
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

            // -- Consent receipt bar --
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

            // -- Done button --
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
