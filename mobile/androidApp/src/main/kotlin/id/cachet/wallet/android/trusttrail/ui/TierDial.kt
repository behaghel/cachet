package id.cachet.wallet.android.trusttrail.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.theme.BrandAccent
import id.cachet.wallet.android.ui.theme.SurfaceBorder

/**
 * Total arc sweep in degrees. The C-shape opens at the bottom,
 * matching the Cachet logo "C" opening.
 * Start angle = 135° (lower-left), sweep = 270° clockwise → ends at 45° (lower-right).
 */
internal const val DIAL_TOTAL_SWEEP = 270f
internal const val DIAL_START_ANGLE = 135f

/** Track and progress stroke widths. */
private val TRACK_STROKE = 28.dp
private val PROGRESS_STROKE = 24.dp
private val DOT_RADIUS = 6.dp

/** Compute the sweep angle for a given strength [0.0, 1.0]. */
internal fun strengthToSweep(strength: Float): Float =
    strength.coerceIn(0f, 1f) * DIAL_TOTAL_SWEEP

/**
 * C-shaped circular dial gauge showing cachet strength.
 *
 * Renders a grey background track arc (270°, opening at bottom)
 * with a green progress fill proportional to [strength].
 * The [content] slot is centered inside the dial — use it for
 * the shield logo, tier badge, and strength percentage.
 *
 * @param strength Value between 0.0 and 1.0
 * @param dialSize Outer diameter of the dial
 * @param content Composable content centered inside the dial ring
 */
@Composable
fun TierDial(
    strength: Float,
    modifier: Modifier = Modifier,
    dialSize: Dp = 260.dp,
    content: @Composable () -> Unit = {},
) {
    val sweepAngle = strengthToSweep(strength)

    Box(
        modifier = modifier
            .size(dialSize)
            .testTag("tier_dial"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(dialSize)) {
            val strokePx = TRACK_STROKE.toPx()
            val progressPx = PROGRESS_STROKE.toPx()
            val dotPx = DOT_RADIUS.toPx()
            val padding = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(padding, padding)

            // Background track — full C arc (grey)
            drawArc(
                color = SurfaceBorder,
                startAngle = DIAL_START_ANGLE,
                sweepAngle = DIAL_TOTAL_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Progress fill (green) — only if strength > 0
            if (sweepAngle > 0f) {
                drawArc(
                    color = BrandAccent,
                    startAngle = DIAL_START_ANGLE,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = progressPx, cap = StrokeCap.Round),
                )

                // Current position dot at the end of the progress arc
                val endAngleRad = Math.toRadians((DIAL_START_ANGLE + sweepAngle).toDouble())
                val cx = size.width / 2f + (arcSize.width / 2f) * kotlin.math.cos(endAngleRad).toFloat()
                val cy = size.height / 2f + (arcSize.height / 2f) * kotlin.math.sin(endAngleRad).toFloat()
                drawCircle(
                    color = Color.White,
                    radius = dotPx + 2.dp.toPx(),
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = BrandAccent,
                    radius = dotPx,
                    center = Offset(cx, cy),
                )
            }
        }

        // Content centered inside the dial ring
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}
