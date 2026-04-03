package id.cachet.wallet.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.CachetMark
import id.cachet.wallet.android.ui.components.SealButton
import id.cachet.wallet.android.ui.theme.*

private data class OnboardingPage(
    val title: String,
    val description: String,
    val keyIcon: String,
    val keyTitle: String,
    val keySubtitle: String,
    val ctaLabel: String
)

private val pages = listOf(
    OnboardingPage(
        title = "Your trust, portable",
        description = "Prove what matters about you\nwithout exposing what doesn't.",
        keyIcon = "\uD83D\uDD12",
        keyTitle = "Your data never leaves your phone.",
        keySubtitle = "Not even Cachet can see it.",
        ctaLabel = "Next"
    ),
    OnboardingPage(
        title = "Cachets, not\ndata breaches",
        description = "Cachets prove specific things —\n\"I'm 18+\", not your whole identity.\nReuse them everywhere.",
        keyIcon = "\u267B\uFE0F",
        keyTitle = "Cache once, prove many times.",
        keySubtitle = "No repeated ID checks.",
        ctaLabel = "Next"
    ),
    OnboardingPage(
        title = "Every share,\non the record",
        description = "Each time you share a proof, a receipt\nis created and logged publicly.\nYou can audit anytime.",
        keyIcon = "\uD83D\uDCCB",
        keyTitle = "Nobody can deny what happened.",
        keySubtitle = "Tamper-proof transparency log.",
        ctaLabel = "Get Started"
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val page = pages[currentPage]

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
            // ── Skip ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                if (currentPage < pages.lastIndex) {
                    Text(
                        text = "Skip",
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable { onComplete() },
                        style = MaterialTheme.typography.labelLarge,
                        color = TrustNeutral
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Illustration ──
            when (currentPage) {
                0 -> LogoIllustration()
                1 -> CachetCardsIllustration()
                2 -> ReceiptListIllustration()
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Title ──
            Text(
                text = page.title,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Description ──
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Key point card ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF334155)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = page.keyIcon,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = page.keyTitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = Color.White
                        )
                        Text(
                            text = page.keySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TrustNeutral
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Pagination dots ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = if (index == currentPage) Color.White else TrustNeutral
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── CTA ──
            SealButton(
                text = page.ctaLabel,
                onClick = {
                    if (currentPage < pages.lastIndex) {
                        currentPage++
                    } else {
                        onComplete()
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Page 1 illustration: Cachet logo mark ──

@Composable
private fun LogoIllustration() {
    Canvas(modifier = Modifier.size(200.dp)) {
        val cx = size.width / 2
        val cy = size.height / 2

        // Outer ring
        drawCircle(
            color = Color(0xFF334155),
            radius = size.minDimension / 2,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )
        // Back circle
        drawCircle(
            color = BrandPrimary,
            radius = 72.dp.toPx(),
            center = Offset(cx, cy - 10.dp.toPx())
        )
        // Front circle (emerald)
        drawCircle(
            color = BrandAccent,
            radius = 58.dp.toPx(),
            center = Offset(cx, cy + 10.dp.toPx())
        )
        // White C arc
        val arcPath = Path().apply {
            addArc(
                oval = androidx.compose.ui.geometry.Rect(
                    left = cx - 55.dp.toPx(),
                    top = cy - 45.dp.toPx(),
                    right = cx + 55.dp.toPx(),
                    bottom = cy + 65.dp.toPx()
                ),
                startAngleDegrees = 55f,
                sweepAngleDegrees = 290f
            )
        }
        drawPath(
            arcPath, Color.White,
            style = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

// ── Page 2 illustration: fanned cachet cards ──

@Composable
private fun CachetCardsIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back card (rotated)
        Surface(
            modifier = Modifier
                .width(230.dp)
                .height(140.dp)
                .offset(y = (-10).dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF334155),
            shadowElevation = 2.dp
        ) {}

        // Mid card
        Surface(
            modifier = Modifier
                .width(230.dp)
                .height(140.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF064E3B),
            shadowElevation = 4.dp
        ) {}

        // Front card
        Surface(
            modifier = Modifier
                .width(230.dp)
                .height(140.dp)
                .offset(y = 10.dp),
            shape = RoundedCornerShape(16.dp),
            color = BrandAccent,
            shadowElevation = 6.dp
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                CachetMark(type = CachetType.CHILDCARE, size = 56.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Childcare Ready",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Valid 90 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD1FAE5)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("✓ Age 18+", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    Text("✓ Identity verified", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    Text("✓ Background clear", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ── Page 3 illustration: receipt list mock ──

@Composable
private fun ReceiptListIllustration() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReceiptMockRow("Childcare check — Mar 15", "Parents Association", "Logged", BrandAccent)
        ReceiptMockRow("Age verification — Mar 12", "Concert venue", "Logged", BrandAccent)
        ReceiptMockRow("Seller cachet — Mar 10", "Marketplace", "Pending", TrustPending)
    }
}

@Composable
private fun ReceiptMockRow(
    title: String,
    subtitle: String,
    status: String,
    statusColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF334155)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = statusColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (status == "Logged") "✓" else "⏳",
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodySmall, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TrustNeutral)
            }
            Text(
                status,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = statusColor
            )
        }
    }
}
