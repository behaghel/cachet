package id.cachet.wallet.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
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

// ═══════════════════════════════════════════
// BRAND SHIELD (green logo, 400x480 viewBox)
// ═══════════════════════════════════════════

// Path data from design/wireframes/holder-01-onboarding-1.svg  <symbol id="brand-shield">
private const val BRAND_OUTER   = "M 200 32 C 240 55, 348 58, 366 59 V 221 C 366 311, 296 382, 200 418 C 104 382, 34 311, 34 221 V 59 C 52 58, 160 55, 200 32 Z"
private const val BRAND_RIM     = "M 200 40 C 238 54, 340 63, 362 63 V 219 C 362 307, 294 376, 200 412 C 106 376, 38 307, 38 219 V 63 C 60 63, 162 54, 200 40 Z"
private const val BRAND_INNER   = "M 200 47 C 234 63, 334 68, 354 71 V 218 C 354 301, 288 367, 200 401 C 112 367, 46 301, 46 218 V 71 C 66 68, 166 63, 200 47 Z"
private const val BRAND_LISERE  = "M 200 54 C 230 64, 328 74, 350 73 V 218 C 350 297, 286 363, 200 397 C 114 363, 50 297, 50 218 V 73 C 72 74, 170 64, 200 54 Z"
private const val BRAND_BODY    = "M 200 62 C 228 71, 322 78, 342 81 V 218 C 342 294, 280 356, 200 388 C 120 356, 58 294, 58 218 V 81 C 78 78, 172 71, 200 62 Z"
private const val BRAND_BODY_R  = "M 200 62 C 172 71, 78 78, 58 81 V 218 C 58 294, 120 356, 200 388 Z"
private const val BRAND_FRONT_R = "M 200 131 C 280 131, 330 152, 326 257 C 324 320, 268 358, 200 390 Z"
private const val BRAND_FRONT_L = "M 200 131 C 120 131, 70 152, 74 257 C 76 320, 132 358, 200 390 Z"
private const val BRAND_C_ARC   = "M 274 308 A 108 108 0 1 0 126 308"

private val brandOuterPath   by lazy { PathParser().parsePathString(BRAND_OUTER).toPath() }
private val brandRimPath     by lazy { PathParser().parsePathString(BRAND_RIM).toPath() }
private val brandInnerPath   by lazy { PathParser().parsePathString(BRAND_INNER).toPath() }
private val brandLiserePath  by lazy { PathParser().parsePathString(BRAND_LISERE).toPath() }
private val brandBodyPath    by lazy { PathParser().parsePathString(BRAND_BODY).toPath() }
private val brandBodyRPath   by lazy { PathParser().parsePathString(BRAND_BODY_R).toPath() }
private val brandFrontRPath  by lazy { PathParser().parsePathString(BRAND_FRONT_R).toPath() }
private val brandFrontLPath  by lazy { PathParser().parsePathString(BRAND_FRONT_L).toPath() }
private val brandCArcPath    by lazy { PathParser().parsePathString(BRAND_C_ARC).toPath() }

private const val BRAND_VB_W = 400f
private const val BRAND_VB_H = 480f

/**
 * Brand logo mark — the green shield with white C arc, no inner icon.
 * Matches design/logo/logo-mark.svg exactly.
 *
 * @param size The height of the mark. Width is derived from the aspect ratio.
 * @param fillWidth When true, fills available width and centers the mark (for hero layouts).
 */
@Composable
fun BrandShieldMark(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    fillWidth: Boolean = true
) {
    val widthDp = size * (BRAND_VB_W / BRAND_VB_H)
    val canvasModifier = if (fillWidth) {
        modifier.fillMaxWidth().height(size)
    } else {
        modifier.size(width = widthDp, height = size)
    }

    Canvas(modifier = canvasModifier) {
        val scale = this.size.height / BRAND_VB_H
        // Center when filling width; no offset when intrinsically sized
        val shieldWidthPx = BRAND_VB_W * scale
        val offsetX = if (fillWidth) (this.size.width - shieldWidthPx) / 2f else 0f

        withTransform({
            translate(left = offsetX)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawPath(brandOuterPath,  color = Color(0xFF34D399))  // light emerald
            drawPath(brandRimPath,    color = Color(0xFF10B981))  // vivid emerald
            drawPath(brandInnerPath,  color = Color(0xFF059669))  // mid emerald
            drawPath(brandLiserePath, color = Color(0xFF6EE7B7))  // pale emerald
            drawPath(brandBodyPath,   color = Color(0xFF1E293B))  // midnight slate
            drawPath(brandBodyRPath,  color = Color(0xFF253347))  // slate reflection
            drawPath(brandFrontRPath, color = Color(0xFF0EA572))  // deep emerald
            drawPath(brandFrontLPath, color = Color(0xFF10B981))  // vivid emerald
            drawPath(
                brandCArcPath, color = Color.White,
                style = Stroke(width = 56f, cap = StrokeCap.Round)
            )
        }
    }
}
