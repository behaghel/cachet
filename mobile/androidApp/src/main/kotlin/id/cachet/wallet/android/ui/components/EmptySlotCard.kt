package id.cachet.wallet.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.theme.*

/**
 * Dashed-border placeholder card with a "+" icon and label.
 * Used in the cachet grid to invite users to add a new cachet.
 */
@Composable
fun EmptySlotCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Get a new\ncachet"
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .border(
                width = 2.dp,
                color = SurfaceBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceBackground
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = SurfaceElevated
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextTertiary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
