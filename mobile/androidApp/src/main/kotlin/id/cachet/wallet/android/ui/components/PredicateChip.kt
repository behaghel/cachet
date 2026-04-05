package id.cachet.wallet.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.theme.*

/**
 * Human-readable predicate pill: "Age 18+", "ID Verified", "Criminal clear".
 * Verified = emerald tint. Unverified = neutral grey.
 */
@Composable
fun PredicateChip(
    label: String,
    verified: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (verified) TrustVerifiedBg else SurfaceElevated
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (verified) TrustVerifiedText else TextTertiary
        )
    }
}
