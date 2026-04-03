package id.cachet.wallet.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.theme.*

/**
 * "My Trust / Verify" segmented control.
 * Active tab gets a slate pill. Inactive tab is transparent.
 */
@Composable
fun CachetSegmentedControl(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceElevated)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) BrandPrimary else SurfaceElevated,
                animationSpec = tween(200),
                label = "tab-bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) TextOnBrand else TextSecondary,
                animationSpec = tween(200),
                label = "tab-text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
                    .clickable { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}
