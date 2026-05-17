package id.cachet.wallet.android.ui.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.components.CachetMark
import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.SealButton
import id.cachet.wallet.android.ui.theme.*

// ── Per-type accent color for pack name on liveness screen ──

fun lisereForType(type: CachetType): Color = when (type) {
    CachetType.CHILDCARE -> ShieldChildcareLisere
    CachetType.SELLER -> ShieldSellerLisere
    CachetType.AGE -> ShieldAgeLisere
    CachetType.IDENTITY -> ShieldIdentityLisere
    CachetType.TRUSTED_HOST -> ShieldHostLisere
}

// ── Step indicator (shared by both screens) ──

enum class StepState { DONE, ACTIVE, FAILED, PENDING }

@Composable
fun StepIndicator(steps: List<StepState>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, state ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(SurfaceBorderDark)
                )
            }
            when (state) {
                StepState.DONE -> Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(BrandAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = TextOnBrand
                    )
                }
                StepState.ACTIVE -> Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(BrandAccent)
                        .border(2.dp, BrandAccentLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TextOnBrand)
                    )
                }
                StepState.FAILED -> Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(BrandWarm)
                        .border(2.dp, TrustRevokedBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PriorityHigh,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = TextOnBrand
                    )
                }
                StepState.PENDING -> Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(SurfaceBorderDark)
                )
            }
        }
    }
}

/**
 * Liveness check screen — shown between consent and signing for high-value packs.
 *
 * In demo mode, shows a simulated camera area with pass/fail buttons.
 * In production, this will launch the Veriff SDK.
 */
@Composable
fun LivenessCheckScreen(
    packName: String,
    cachetType: CachetType = CachetType.CHILDCARE,
    onSimulatePass: () -> Unit,
    onSimulateFail: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("liveness_check_screen"),
        color = BrandPrimaryDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // ── Top bar: close X (right-aligned) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondaryDark)
                }
            }

            // ── Step indicator ──
            StepIndicator(listOf(StepState.DONE, StepState.ACTIVE, StepState.PENDING))

            Spacer(modifier = Modifier.height(24.dp))

            // ── Pack badge ──
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CachetMark(type = cachetType, size = 56.dp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Header ──
            Text(
                text = "Prove it\u2019s you",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextOnBrand,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A quick identity check is required for",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = packName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = lisereForType(cachetType),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Camera area (demo placeholder) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(BrandPrimary)
                    .border(2.dp, SurfaceBorderDark, CircleShape)
                    .testTag("liveness_camera_area"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Camera preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = TrustNeutral
                    )
                    Text(
                        text = "(Veriff SDK in production)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Trust explanation card ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = BrandPrimary,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = BrandAccent
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "This check ensures only you can sign this high-value verification response.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Demo controls: Simulate Pass / Simulate Fail ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SealButton(
                        text = "Simulate Pass",
                        onClick = onSimulatePass
                    )
                }
                OutlinedButton(
                    onClick = onSimulateFail,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, SurfaceBorderDark)
                ) {
                    Text(
                        "Simulate Fail",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = BrandWarm
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Cancel ──
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryDark
                )
            }

            Spacer(modifier = Modifier
                .navigationBarsPadding()
                .height(8.dp))
        }
    }
}

/**
 * Liveness failure screen — shown when the Veriff identity check fails.
 * Deliberately vague about failure reason (security).
 */
@Composable
fun LivenessFailedScreen(
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("liveness_failed_screen"),
        color = BrandPrimaryDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar: close X (right-aligned) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondaryDark)
                }
            }

            // ── Step indicator ──
            StepIndicator(listOf(StepState.DONE, StepState.FAILED, StepState.PENDING))

            Spacer(modifier = Modifier.height(64.dp))

            // ── Failure icon — Material Close icon, properly centered ──
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(BrandPrimary)
                    .border(2.dp, TrustRevokedText, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Failed",
                    modifier = Modifier.size(64.dp),
                    tint = BrandWarm
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "We couldn\u2019t confirm it\u2019s you",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextOnBrand,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The identity check didn\u2019t pass.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center
            )
            Text(
                text = "No credentials were shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Tips card ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BrandPrimary,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tips for next time",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextOnBrand
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    for (tip in listOf(
                        "Make sure your face is well-lit",
                        "Remove sunglasses or face coverings",
                        "Look straight at the camera, at eye level",
                        "Look as you did when you set up your ID"
                    )) {
                        Text(
                            text = "\u2022  $tip",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Retry button (SealButton) ──
            SealButton(
                text = "Try Again",
                onClick = onRetry
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Cancel ──
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Cancel verification",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = BrandWarm
                )
            }

            Spacer(modifier = Modifier
                .navigationBarsPadding()
                .height(8.dp))
        }
    }
}
