package id.cachet.wallet.android.ui.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.theme.*

/**
 * Liveness check screen — shown between consent and signing for high-value packs.
 *
 * In demo mode, shows a simulated camera area with pass/fail buttons.
 * In production, this will launch the Veriff SDK.
 */
@Composable
fun LivenessCheckScreen(
    packName: String,
    onSimulatePass: () -> Unit,
    onSimulateFail: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("liveness_check_screen"),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Step indicator: Consent [done] > Liveness [active] > Result [pending]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step 1: Consent (done)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(BrandAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u2713", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(Color(0xFF334155))
                )
                // Step 2: Liveness (active)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(BrandAccent)
                        .border(2.dp, Color(0xFF34D399), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(Color(0xFF334155))
                )
                // Step 3: Result (pending)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Header
            Text(
                text = "Prove it\u2019s you",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "A quick identity check is required for",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = packName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD88AA0),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Camera area (demo placeholder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(2.dp, Color(0xFF334155), CircleShape)
                    .testTag("liveness_camera_area"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Camera preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "(Veriff SDK in production)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Trust explanation
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Text("\uD83D\uDEE1\uFE0F", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "This check ensures only you can sign this high-value verification response.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Demo controls: Simulate Pass / Simulate Fail
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSimulatePass,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Simulate Pass", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onSimulateFail,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Simulate Fail", color = Color(0xFFF97068))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cancel
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }

            Spacer(modifier = Modifier.height(24.dp))
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
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Step indicator: Consent [done] > Liveness [failed] > Result [pending]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(BrandAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u2713", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.width(24.dp).height(2.dp).background(Color(0xFF334155)))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .border(2.dp, Color(0xFFF87171), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.width(24.dp).height(2.dp).background(Color(0xFF334155)))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Failure icon
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(2.dp, Color(0xFF7F1D1D), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("\u2717", fontSize = 48.sp, color = Color(0xFFEF4444))
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "We couldn\u2019t confirm it\u2019s you",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The identity check didn\u2019t pass.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
            Text(
                text = "No credentials were shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Tips
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tips for next time",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
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
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Retry button
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandAccent),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Try Again", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cancel
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel verification", color = Color(0xFFF97068))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
