package id.cachet.wallet.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import id.cachet.wallet.android.ui.components.QrCodeImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.BrandShieldMark
import id.cachet.wallet.android.ui.model.QrShareState
import id.cachet.wallet.android.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QrShareScreen(
    state: QrShareState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onShareLink: () -> Unit = {},
    onScanSimulated: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BrandPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar: back + close ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextTertiary
                    )
                }
            }

            // ── Title ──
            Text(
                text = state.question,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Show this code to the person you want to cache",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── QR code area ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(20.dp),
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.qrPayload.isNotBlank()) {
                        QrCodeImage(
                            content = state.qrPayload,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                        )
                    } else {
                        QrPatternPlaceholder()
                    }
                    BrandShieldMark(size = 48.dp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── "This will verify" + predicate chips ──
            Text(
                text = "This will cache:",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                state.predicates.forEach { label ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF334155)
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Waiting state ──
            Text(
                text = "Waiting for them to scan...",
                style = MaterialTheme.typography.labelLarge,
                color = TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.4f, 0.7f, 1f).forEach { alpha ->
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = TrustNeutral.copy(alpha = alpha)
                    ) {}
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Share link button ──
            OutlinedButton(
                onClick = onShareLink,
                modifier = Modifier
                    .width(215.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, TrustNeutral)
            ) {
                Text(
                    "Share link instead",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Expiry label ──
            Text(
                text = state.expiresLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TrustNeutral
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ── QR pattern placeholder (geometric, not real QR) ──

@Composable
private fun QrPatternPlaceholder() {
    Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        val s = size.minDimension
        val blockSize = s / 16f
        val dark = Color(0xFF1E293B)

        // Simplified QR finder pattern corners
        fun drawFinder(cx: Float, cy: Float) {
            drawRoundRect(dark, Offset(cx - 3 * blockSize, cy - 3 * blockSize),
                Size(6 * blockSize, 6 * blockSize), CornerRadius(blockSize * 0.5f),
                style = Stroke(width = blockSize * 0.8f))
            drawRect(dark, Offset(cx - blockSize, cy - blockSize),
                Size(2 * blockSize, 2 * blockSize))
        }

        drawFinder(3 * blockSize, 3 * blockSize)
        drawFinder(s - 3 * blockSize, 3 * blockSize)
        drawFinder(3 * blockSize, s - 3 * blockSize)

        // Random data blocks to simulate QR pattern
        val positions = listOf(
            7 to 3, 8 to 4, 9 to 3, 10 to 5,
            7 to 7, 8 to 8, 10 to 7, 11 to 8,
            3 to 8, 4 to 9, 5 to 8,
            12 to 3, 13 to 4, 12 to 5,
            3 to 12, 4 to 11, 5 to 13,
            12 to 12, 13 to 11, 11 to 13
        )
        positions.forEach { (x, y) ->
            drawRect(dark, Offset(x * blockSize, y * blockSize),
                Size(blockSize, blockSize))
        }
    }
}

