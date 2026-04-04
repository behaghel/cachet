package id.cachet.wallet.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.cachet.wallet.android.ui.theme.*

/**
 * Renders a Cachet shield mark at any size.
 * Geometry faithfully matches the Inkscape-edited SVG template (400x520 viewBox).
 */

enum class CachetType { CHILDCARE, SELLER, AGE, IDENTITY }

data class ShieldColors(
    val outerRim: Color,
    val rim: Color,
    val innerRim: Color,
    val lisere: Color,
    val frontRight: Color,
    val frontLeft: Color
)

private fun shieldColorsFor(type: CachetType) = when (type) {
    CachetType.CHILDCARE -> ShieldColors(
        ShieldChildcareOuterRim, ShieldChildcareRim, ShieldChildcareInnerRim,
        ShieldChildcareLisere, ShieldChildcareFrontR, ShieldChildcareFrontL
    )
    CachetType.SELLER -> ShieldColors(
        ShieldSellerOuterRim, ShieldSellerRim, ShieldSellerInnerRim,
        ShieldSellerLisere, ShieldSellerFrontR, ShieldSellerFrontL
    )
    CachetType.AGE -> ShieldColors(
        ShieldAgeOuterRim, ShieldAgeRim, ShieldAgeInnerRim,
        ShieldAgeLisere, ShieldAgeFrontR, ShieldAgeFrontL
    )
    CachetType.IDENTITY -> ShieldColors(
        ShieldIdentityOuterRim, ShieldIdentityRim, ShieldIdentityInnerRim,
        ShieldIdentityLisere, ShieldIdentityFrontR, ShieldIdentityFrontL
    )
}

// ── SVG path data (symmetric around x=200, 400x520 viewBox) ──

private const val PATH_OUTER_RIM =
    "M 200 35 C 240 60, 348 63, 366 64 V 239 C 366 337, 296 414, 200 453 C 104 414, 34 337, 34 239 V 64 C 52 63, 160 60, 200 35 Z"

private const val PATH_RIM =
    "M 200 43 C 238 59, 340 68, 362 68 V 237 C 362 333, 294 407, 200 446 C 106 407, 38 333, 38 237 V 68 C 60 68, 162 59, 200 43 Z"

private const val PATH_INNER_RIM =
    "M 200 51 C 234 68, 334 74, 354 77 V 236 C 354 326, 288 397, 200 434 C 112 397, 46 326, 46 236 V 77 C 66 74, 166 68, 200 51 Z"

private const val PATH_LISERE =
    "M 200 59 C 230 69, 328 80, 350 79 V 236 C 350 322, 286 393, 200 430 C 114 393, 50 322, 50 236 V 79 C 72 80, 170 69, 200 59 Z"

private const val PATH_BODY =
    "M 200 67 C 228 77, 322 85, 342 88 V 236 C 342 318, 280 386, 200 420 C 120 386, 58 318, 58 236 V 88 C 78 85, 172 77, 200 67 Z"

private const val PATH_BODY_REFLECTION =
    "M 200 67 C 172 77, 78 85, 58 88 V 236 C 58 318, 120 386, 200 420 Z"

private const val PATH_FRONT_RIGHT =
    "M 200 142 C 280 142, 330 165, 326 278 C 324 347, 268 388, 200 422 Z"

private const val PATH_FRONT_LEFT =
    "M 200 142 C 120 142, 70 165, 74 278 C 76 347, 132 388, 200 422 Z"

private const val PATH_C_ARC =
    "M 274 334 A 108 117 0 1 0 126 334"

// Parsed paths — lazy singletons so we parse once
private val outerRimPath by lazy { PathParser().parsePathString(PATH_OUTER_RIM).toPath() }
private val rimPath by lazy { PathParser().parsePathString(PATH_RIM).toPath() }
private val innerRimPath by lazy { PathParser().parsePathString(PATH_INNER_RIM).toPath() }
private val liserePath by lazy { PathParser().parsePathString(PATH_LISERE).toPath() }
private val bodyPath by lazy { PathParser().parsePathString(PATH_BODY).toPath() }
private val bodyReflectionPath by lazy { PathParser().parsePathString(PATH_BODY_REFLECTION).toPath() }
private val frontRightPath by lazy { PathParser().parsePathString(PATH_FRONT_RIGHT).toPath() }
private val frontLeftPath by lazy { PathParser().parsePathString(PATH_FRONT_LEFT).toPath() }
private val cArcPath by lazy { PathParser().parsePathString(PATH_C_ARC).toPath() }

private const val VIEWBOX_W = 400f
private const val VIEWBOX_H = 520f
private const val C_ARC_STROKE = 61f

@Composable
fun CachetMark(
    type: CachetType,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val colors = shieldColorsFor(type)
    val widthDp = size * (VIEWBOX_W / VIEWBOX_H)

    Canvas(modifier = modifier.size(width = widthDp, height = size)) {
        val scale = this.size.height / VIEWBOX_H

        withTransform({ scale(scale, scale) }) {
            // Border layers (back to front)
            drawPath(outerRimPath, color = colors.outerRim)
            drawPath(rimPath, color = colors.rim)
            drawPath(innerRimPath, color = colors.innerRim)
            drawPath(liserePath, color = colors.lisere)

            // Shield body
            drawPath(bodyPath, color = ShieldBody)
            drawPath(bodyReflectionPath, color = ShieldBodyReflection)

            // Front accent areas
            drawPath(frontRightPath, color = colors.frontRight)
            drawPath(frontLeftPath, color = colors.frontLeft)

            // White C arc
            drawPath(
                cArcPath, color = Color.White,
                style = Stroke(width = C_ARC_STROKE, cap = StrokeCap.Round)
            )

            // Icon (apply SVG's group transform then draw)
            withTransform({
                transform(Matrix().apply {
                    // matrix(1.3514, 0, 0, 1.3514, -70.28, -51.0)
                    values[Matrix.ScaleX] = 1.3514f
                    values[Matrix.ScaleY] = 1.3514f
                    values[Matrix.TranslateX] = -70.28f
                    values[Matrix.TranslateY] = -51.0f
                })
            }) {
                when (type) {
                    CachetType.CHILDCARE -> drawBabyFace()
                    CachetType.SELLER -> drawDollarSign()
                    CachetType.AGE -> drawAgeText()
                    CachetType.IDENTITY -> drawCheckmark()
                }
            }
        }
    }
}

// ── Icon drawing (coordinates in the icon's local space, before group transform) ──

private fun DrawScope.drawCheckmark() {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(174f, 222f)
        lineTo(194f, 246f)
        lineTo(230f, 196f)
    }
    drawPath(
        path, Color.White.copy(alpha = 0.9f),
        style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawBabyFace() {
    val sw = 5f
    // Head circle
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = 36f,
        center = Offset(200f, 222f),
        style = Stroke(width = sw)
    )
    // Eyes
    drawCircle(Color.White.copy(alpha = 0.9f), 4f, Offset(186f, 214f))
    drawCircle(Color.White.copy(alpha = 0.9f), 4f, Offset(214f, 214f))
    // Smile
    val smilePath = androidx.compose.ui.graphics.Path().apply {
        moveTo(186f, 234f)
        quadraticBezierTo(200f, 248f, 214f, 234f)
    }
    drawPath(
        smilePath, Color.White.copy(alpha = 0.85f),
        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
    )
    // Hair tuft
    val hairPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(183f, 188f)
        quadraticBezierTo(192f, 172f, 200f, 182f)
        quadraticBezierTo(208f, 172f, 217f, 188f)
    }
    drawPath(
        hairPath, Color.White.copy(alpha = 0.9f),
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawDollarSign() {
    // Vertical bar
    drawLine(
        Color.White.copy(alpha = 0.9f),
        Offset(200f, 192f), Offset(200f, 254f),
        strokeWidth = 5f, cap = StrokeCap.Round
    )
    // S curve
    val sPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(218f, 202f)
        cubicTo(210f, 190f, 180f, 192f, 180f, 208f)
        cubicTo(180f, 224f, 220f, 224f, 220f, 240f)
        cubicTo(220f, 256f, 190f, 260f, 182f, 248f)
    }
    drawPath(
        sPath, Color.White.copy(alpha = 0.9f),
        style = Stroke(width = 5.5f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawAgeText() {
    val sw = 5f
    // "1" — vertical stroke + serif
    drawLine(
        Color.White.copy(alpha = 0.9f),
        Offset(176f, 204f), Offset(176f, 244f),
        strokeWidth = sw, cap = StrokeCap.Round
    )
    drawLine(
        Color.White.copy(alpha = 0.9f),
        Offset(168f, 204f), Offset(176f, 204f),
        strokeWidth = 3.5f, cap = StrokeCap.Round
    )
    // "8" — two stacked circles
    drawCircle(
        Color.White.copy(alpha = 0.9f), 8f, Offset(198f, 214f),
        style = Stroke(width = 4.5f)
    )
    drawCircle(
        Color.White.copy(alpha = 0.9f), 9f, Offset(198f, 234f),
        style = Stroke(width = 4.5f)
    )
    // "+" — cross
    drawLine(
        Color.White.copy(alpha = 0.9f),
        Offset(218f, 224f), Offset(236f, 224f),
        strokeWidth = 4f, cap = StrokeCap.Round
    )
    drawLine(
        Color.White.copy(alpha = 0.9f),
        Offset(227f, 215f), Offset(227f, 233f),
        strokeWidth = 4f, cap = StrokeCap.Round
    )
}
