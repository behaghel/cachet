package id.cachet.wallet.android.trusttrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.ui.theme.SurfaceBackground
import id.cachet.wallet.android.ui.theme.TextTertiary
import id.cachet.wallet.trusttrail.strength.Tier

/** Returns the display name for a tier, or null if below bronze. */
internal fun tierDisplayName(tier: Tier?): String? = when (tier) {
    Tier.BRONZE -> "BRONZE"
    Tier.SILVER -> "SILVER"
    Tier.GOLD -> "GOLD"
    null -> null
}

/** Metallic gradient brush per tier. */
private fun tierGradient(tier: Tier): Brush = when (tier) {
    Tier.BRONZE -> Brush.linearGradient(
        listOf(Color(0xFFCD7F32), Color(0xFFB87333), Color(0xFFDAA06D), Color(0xFFB87333))
    )
    Tier.SILVER -> Brush.linearGradient(
        listOf(Color(0xFFE5E7EB), Color(0xFF9CA3AF), Color(0xFFD1D5DB), Color(0xFF9CA3AF))
    )
    Tier.GOLD -> Brush.linearGradient(
        listOf(Color(0xFFFFD700), Color(0xFFDAA520), Color(0xFFFFF8DC), Color(0xFFDAA520))
    )
}

/** Text color per tier — metallic tint. */
private fun tierTextColor(tier: Tier): Color = when (tier) {
    Tier.BRONZE -> Color(0xFFB87333)
    Tier.SILVER -> Color(0xFF71717A)
    Tier.GOLD -> Color(0xFFDAA520)
}

/**
 * Metallic pill badge showing the current tier name.
 * Hidden (emits nothing) when [tier] is null (below bronze).
 *
 * Pill shape with gradient border, dark inset, letter-spaced tier text.
 */
@Composable
fun TierBadge(
    tier: Tier?,
    modifier: Modifier = Modifier,
) {
    if (tier == null) return

    val shape = RoundedCornerShape(15.dp)

    Box(
        modifier = modifier
            .testTag("tier_badge")
            .border(width = 1.5.dp, brush = tierGradient(tier), shape = shape)
            .background(color = SurfaceBackground, shape = shape)
            .padding(horizontal = 20.dp, vertical = 5.dp),
    ) {
        Text(
            text = tierDisplayName(tier)!!,
            fontSize = 13.sp,
            fontWeight = FontWeight(800),
            letterSpacing = 1.5.sp,
            color = tierTextColor(tier),
        )
    }
}
