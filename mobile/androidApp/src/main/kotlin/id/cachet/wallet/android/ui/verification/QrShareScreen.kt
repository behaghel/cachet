package id.cachet.wallet.android.ui.verification

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
import androidx.compose.ui.platform.testTag
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
    onRetry: () -> Unit = {}
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var sessionStatus by remember { mutableStateOf(id.cachet.wallet.android.ui.model.QrSessionStatus.WAITING) }

    LaunchedEffect(state) {
        elapsedSeconds = 0
        sessionStatus = id.cachet.wallet.android.ui.model.QrSessionStatus.WAITING
        while (elapsedSeconds < state.sessionTtlSeconds) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
        sessionStatus = id.cachet.wallet.android.ui.model.QrSessionStatus.EXPIRED
    }

    val remaining = (state.sessionTtlSeconds - elapsedSeconds).coerceAtLeast(0)
    val minutes = remaining / 60
    val seconds = remaining % 60
    val timerLabel = "%d:%02d".format(minutes, seconds)

    Surface(
        modifier = Modifier.fillMaxSize().testTag("qr_share_screen"),
        color = BrandPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar: back + close ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
                text = "Show this code to the person you want to verify",
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
                text = "This will verify:",
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

            // ── Waiting / Expired state ──
            if (sessionStatus == id.cachet.wallet.android.ui.model.QrSessionStatus.EXPIRED) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF334155)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Session expired",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandWarm
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The QR code is no longer valid. Start a new session to try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandAccent)
                        ) {
                            Text(
                                "Try again",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = BrandAccent
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Waiting for scan...  $timerLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextTertiary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pulse = (elapsedSeconds % 3)
                    listOf(0, 1, 2).forEach { i ->
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = TrustNeutral.copy(alpha = if (i == pulse) 1f else 0.4f)
                        ) {}
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Share link button (hidden when expired) ──
            if (sessionStatus != id.cachet.wallet.android.ui.model.QrSessionStatus.EXPIRED) {
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

                // ── Expiry countdown ──
                Text(
                    text = "Request expires in $timerLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (remaining < 60) BrandWarm else TrustNeutral
                )
            }

            Spacer(modifier = Modifier
                .navigationBarsPadding()
                .height(8.dp))
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

