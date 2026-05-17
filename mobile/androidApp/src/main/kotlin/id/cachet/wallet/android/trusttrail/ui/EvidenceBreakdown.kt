package id.cachet.wallet.android.trusttrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.cachet.wallet.android.trusttrail.model.PlatformContributionUi
import id.cachet.wallet.android.ui.theme.*

/** Format evidence count with singular/plural. */
internal fun evidenceCountLabel(count: Int): String =
    if (count == 1) "1 evidence item" else "$count evidence items"

/**
 * Evidence breakdown section showing per-platform contribution.
 * Each platform row has: name, evidence count, contribution %, progress bar.
 */
@Composable
fun EvidenceBreakdown(
    platforms: List<PlatformContributionUi>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = SurfaceBorder)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Evidence",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.testTag("evidence_header"),
        )
        Spacer(modifier = Modifier.height(12.dp))
        platforms.forEachIndexed { index, platform ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            PlatformRow(platform)
        }
    }
}

@Composable
private fun PlatformRow(platform: PlatformContributionUi) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("evidence_platform_${platform.platformName}"),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: name + count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platform.platformName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                )
                Text(
                    text = evidenceCountLabel(platform.evidenceCount),
                    fontSize = 11.sp,
                    color = TextTertiary,
                )
            }
            // Right: percentage + bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${platform.contributionPercent}%",
                    fontSize = 11.sp,
                    color = TextTertiary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                ContributionBar(
                    fraction = platform.contributionPercent / 100f,
                    modifier = Modifier.width(112.dp),
                )
            }
        }
    }
}

/**
 * Mini progress bar showing contribution fraction.
 */
@Composable
private fun ContributionBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val barShape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(barShape)
            .background(SurfaceBorder)
            .testTag("contribution_bar"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(barShape)
                .background(BrandAccent),
        )
    }
}
