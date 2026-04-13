package id.cachet.wallet.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.theme.*

/**
 * The outcome "cartridge" — Passed, Incomplete, Pending, Revoked.
 */
enum class TrustStatus {
    PASSED, VERIFIED, INCOMPLETE, PENDING, REVOKED
}

@Composable
fun TrustStatusChip(
    status: TrustStatus,
    modifier: Modifier = Modifier
) {
    val (label, bg, border, text) = when (status) {
        TrustStatus.PASSED -> StatusColors("Passed", TrustVerifiedBg, TrustVerifiedBorder, TrustVerifiedText)
        TrustStatus.VERIFIED -> StatusColors("Verified", TrustVerifiedBg, TrustVerifiedBorder, TrustVerifiedText)
        TrustStatus.INCOMPLETE -> StatusColors("Incomplete", TrustRevokedBg, TrustRevokedBorder, TrustRevokedText)
        TrustStatus.PENDING -> StatusColors("Pending", TrustPendingBg, TrustPendingBorder, TrustPendingText)
        TrustStatus.REVOKED -> StatusColors("Revoked", TrustRevokedBg, TrustRevokedBorder, TrustRevokedText)
    }

    Surface(
        modifier = modifier.testTag("trust_status_chip").border(1.dp, border, RoundedCornerShape(11.dp)),
        shape = RoundedCornerShape(11.dp),
        color = bg
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = text
        )
    }
}

private data class StatusColors(
    val label: String,
    val bg: Color,
    val border: Color,
    val text: Color
)
