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

// ── SVG path data (from Inkscape-edited cachet-shield.svg) ──

private const val PATH_OUTER_RIM =
    "M 200.70553,34.104065 c 39.06139,24.879064 148,28.59103 166,29.575994 V 238.90534 c 0,98.36711 -70,174.46241 -166,213.43806 -96,-38.97565 -166.000016,-115.07095 -166.000016,-213.43806 V 63.680059 C 55.527575,63.349807 151.06141,60.292553 200.70553,34.104065 Z"

private const val PATH_RIM =
    "M 200.70553,43.042672 c 41.29449,15.696708 143.29448,25.291069 162,25.074987 V 236.83601 c 0,96.41048 -68,170.57239 -162,209.50739 -94,-38.935 -162.000016,-113.09691 -162.000016,-209.50739 V 68.117659 C 67.288243,68.333741 158.70552,59.393409 200.70553,43.042672 Z"

private const val PATH_INNER_RIM =
    "M 200.70553,50.453702 c 30.94075,17.44581 138,23.047605 154,26.710977 V 236.52134 c 0,89.7526 -66,161.18835 -154,197.82206 -88,-36.63371 -154.000016,-108.06946 -154.000016,-197.82206 V 77.164679 C 63.411029,75.439731 165.82116,67.253372 200.70553,50.453702 Z"

private const val PATH_LISERE =
    "M 201.41105,57.981279 C 238.70553,68.994317 334.70553,79.793323 350.70553,79.57929 V 235.6785 c 0,86.31368 -64,157.93567 -150,194.6649 -86,-36.72923 -150.000016,-108.35122 -150.000016,-194.6649 V 79.57929 C 67.411029,78.49767 166.23311,68.994317 201.41105,57.981279 Z"

private const val PATH_BODY =
    "M 200.70553,67.392309 c 36.70552,9.012489 128,17.168544 142,20.818354 V 236.02798 c 0,82.12073 -62,149.64222 -142,184.31542 -80,-34.6732 -142.000016,-102.19469 -142.000016,-184.31542 V 88.210663 c 14,-3.64981 111.644136,-11.162116 142.000016,-20.818354 z"

private const val PATH_BODY_REFLECTION =
    "M 200.70553,67.392309 C 165.41105,74.687183 80.466182,84.829985 58.705514,86.383329 V 235.01391 c 0,82.57254 62.000016,150.46553 142.000016,185.32949 z"

private const val PATH_FRONT_RIGHT =
    "M 199.02837,144.11012 c 80.35133,0.0206 131.76031,20.54473 127.77551,136.86212 -2.28823,66.79385 -58.96927,105.79177 -127.77551,142.49916 z"

private const val PATH_FRONT_LEFT =
    "M 199.2945,139.87704 c -73.72974,0.0206 -129.3546,59.78478 -126.417854,136.86213 2.93871,77.12893 79.579574,121.3131 126.417854,144.6157 z"

private const val PATH_C_ARC =
    "M 281.15866,320.17113 a 109.29482,111.08525 0 1 0 -160.90626,0"

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
private const val C_ARC_STROKE = 61.8185f

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
                    // matrix(1.3513653, 0, 0, 1.3513653, -69.567545, -58.371442)
                    values[Matrix.ScaleX] = 1.3513653f
                    values[Matrix.ScaleY] = 1.3513653f
                    values[Matrix.TranslateX] = -69.567545f
                    values[Matrix.TranslateY] = -58.371442f
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
