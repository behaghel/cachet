package id.cachet.wallet.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.theme.BrandAccent
import id.cachet.wallet.android.ui.theme.BrandPrimary
import id.cachet.wallet.android.ui.theme.TextTertiary

/**
 * Direction arrow in a colored circle.
 * GIVEN (↗) = slate circle, white arrow pointing top-right.
 * RECEIVED (↙) = emerald circle, white arrow pointing bottom-left.
 * DECLINED = light grey circle, grey arrow.
 */
enum class VerificationDirection { GIVEN, RECEIVED, DECLINED }

@Composable
fun DirectionIndicator(
    direction: VerificationDirection,
    modifier: Modifier = Modifier
) {
    val size = 36.dp
    val bgColor = when (direction) {
        VerificationDirection.GIVEN -> BrandPrimary
        VerificationDirection.RECEIVED -> BrandAccent
        VerificationDirection.DECLINED -> Color(0xFFF5F5F4)
    }
    val arrowColor = when (direction) {
        VerificationDirection.GIVEN -> Color.White
        VerificationDirection.RECEIVED -> Color.White
        VerificationDirection.DECLINED -> TextTertiary
    }

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = this.size.minDimension / 2

        // Background circle
        drawCircle(color = bgColor, radius = radius, center = center)

        val strokeW = radius * 0.25f
        val arm = radius * 0.55f

        when (direction) {
            VerificationDirection.GIVEN -> {
                // Arrow: bottom-left → top-right
                val from = Offset(center.x - arm, center.y + arm)
                val to = Offset(center.x + arm, center.y - arm)
                drawLine(arrowColor, from, to, strokeWidth = strokeW, cap = StrokeCap.Round)
                // Arrowhead
                drawLine(arrowColor, to, Offset(to.x, to.y + arm * 0.6f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(arrowColor, to, Offset(to.x - arm * 0.6f, to.y), strokeWidth = strokeW, cap = StrokeCap.Round)
            }
            VerificationDirection.RECEIVED, VerificationDirection.DECLINED -> {
                // Arrow: top-right → bottom-left
                val from = Offset(center.x + arm, center.y - arm)
                val to = Offset(center.x - arm, center.y + arm)
                drawLine(arrowColor, from, to, strokeWidth = strokeW, cap = StrokeCap.Round)
                // Arrowhead
                drawLine(arrowColor, to, Offset(to.x, to.y - arm * 0.6f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(arrowColor, to, Offset(to.x + arm * 0.6f, to.y), strokeWidth = strokeW, cap = StrokeCap.Round)
            }
        }
    }
}
