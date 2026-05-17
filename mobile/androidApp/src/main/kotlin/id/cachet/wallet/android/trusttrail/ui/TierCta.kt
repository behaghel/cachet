package id.cachet.wallet.android.trusttrail.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.theme.BrandAccent
import id.cachet.wallet.trusttrail.strength.Tier

/** CTA text adapts to the current tier. */
internal fun tierCtaText(tier: Tier?): String = when (tier) {
    null -> "Scan to reach Bronze"
    Tier.BRONZE -> "Scan to reach Silver"
    Tier.SILVER -> "Scan to reach Gold"
    Tier.GOLD -> "Scan to stay Gold"
}

/**
 * Secondary outlined button CTA at the bottom of the detail screen.
 * Text changes based on current tier.
 */
@Composable
fun TierCtaButton(
    tier: Tier?,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("tier_cta"),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, BrandAccent),
    ) {
        Text(
            text = tierCtaText(tier),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandAccent,
        )
    }
}
