package id.cachet.wallet.android.ui.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.components.CachetMark
import id.cachet.wallet.android.ui.components.CachetType
import id.cachet.wallet.android.ui.components.SealButton
import id.cachet.wallet.android.ui.theme.*

@Composable
fun DeepLinkExpiredScreen(
    cachetType: CachetType = CachetType.CHILDCARE,
    onBackToVault: () -> Unit,
    onScanQr: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onBackToVault) {
                    Text(
                        text = "\u2715",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Cachet shield (full color, never greyed)
            CachetMark(type = cachetType, size = 80.dp)

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Request Expired",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pack name
            Text(
                text = cachetTypeName(cachetType),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Explanation
            Text(
                text = "This verification request is no longer available. For your security, requests expire quickly so your data is never left waiting to be shared.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TrustRevokedBg),
                border = BorderStroke(1.dp, TrustRevokedBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "What to do next",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TrustRevokedText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ask the verifier for a new link or scan their QR code to start a fresh request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TrustRevokedText
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Primary CTA
            SealButton(
                text = "Back to Vault",
                onClick = onBackToVault
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary CTA
            OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, SurfaceBorder)
            ) {
                Text(
                    text = "Scan a QR Code Instead",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun cachetTypeName(type: CachetType): String = when (type) {
    CachetType.CHILDCARE -> "Childcare Ready"
    CachetType.SELLER -> "Safe Seller"
    CachetType.AGE -> "Age Verification"
    CachetType.IDENTITY -> "Identity"
}
