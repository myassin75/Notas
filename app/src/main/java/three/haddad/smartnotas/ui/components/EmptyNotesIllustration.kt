package three.haddad.smartnotas.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.common.isSmallLandscape
import three.haddad.smartnotas.ui.theme.LocalReducedMotion
import three.haddad.smartnotas.ui.theme.MorphPolygonShape
import three.haddad.smartnotas.ui.theme.RoundedPolygonShape

@Composable
fun EmptyNotesIllustration(modifier: Modifier = Modifier) {
    val isSmallLandscape = isSmallLandscape()
    val sizeDp = if (isSmallLandscape) 120.dp else 160.dp

    Box(
        modifier = modifier.size(sizeDp),
        contentAlignment = Alignment.Center,
    ) {
        // Soft lavender background circle
        Box(
            modifier =
                Modifier
                    .size(sizeDp * 0.9f)
                    .clip(CircleShape)
                    .background(Color(0xFFF4EAF6)),
        )

        // Document illustration
        Canvas(modifier = Modifier.size(sizeDp * 0.65f)) {
            val purple = Color(0xFF98549C)
            val strokeWidth = 2.5.dp.toPx()

            // Document body (white card with purple border)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(size.width * 0.15f, size.height * 0.1f),
                size = Size(size.width * 0.65f, size.height * 0.8f),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Fill,
            )
            drawRoundRect(
                color = purple.copy(alpha = 0.35f),
                topLeft = Offset(size.width * 0.15f, size.height * 0.1f),
                size = Size(size.width * 0.65f, size.height * 0.8f),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = strokeWidth),
            )

            // Ruled lines inside document
            val lineXStart = size.width * 0.28f
            val lineXEnd = size.width * 0.68f
            val lineY1 = size.height * 0.30f
            val lineY2 = size.height * 0.45f
            val lineY3 = size.height * 0.60f

            drawLine(purple.copy(alpha = 0.6f), Offset(lineXStart, lineY1), Offset(lineXEnd, lineY1), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(purple.copy(alpha = 0.6f), Offset(lineXStart, lineY2), Offset(lineXEnd * 0.88f, lineY2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(purple.copy(alpha = 0.6f), Offset(lineXStart, lineY3), Offset(lineXEnd * 0.78f, lineY3), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }

        // Circular checkmark badge in bottom right corner
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "check",
                size = 22.dp,
                tint = Color(0xFF98549C),
                weight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun EmptyArchiveIllustration(
    modifier: Modifier = Modifier,
    outlineColor: Color = Color(0xFFAAA3B9),
    circleColor: Color = Color(0xFFF2E8F7),
    cabinetFill: Color = Color.White,
) {
    ArchiveCabinetIllustration(
        modifier = modifier,
        outlineColor = outlineColor,
        circleColor = circleColor,
        cabinetFill = cabinetFill,
    )
}

@Composable
fun EmptyTrashIllustration(
    modifier: Modifier = Modifier,
    outlineColor: Color = Color(0xFFAAA3B9),
    circleColor: Color = Color(0xFFF2E8F7),
    canFill: Color = Color.White,
) {
    TrashCanIllustration(
        modifier = modifier,
        outlineColor = outlineColor,
        circleColor = circleColor,
        canFill = canFill,
    )
}

@Composable
fun ArchiveCabinetIllustration(
    modifier: Modifier = Modifier,
    outlineColor: Color = Color(0xFFAAA3B9),
    circleColor: Color = Color(0xFFF2E8F7),
    cabinetFill: Color = Color.White,
) {
    Canvas(modifier = modifier.size(160.dp)) {
        val w = size.width
        val h = size.height

        // 1. Soft circular decorative shapes behind cabinet
        // Top-left circle
        drawCircle(
            color = circleColor,
            radius = w * 0.22f,
            center = Offset(w * 0.35f, h * 0.38f),
        )
        // Bottom-right circle
        drawCircle(
            color = circleColor,
            radius = w * 0.14f,
            center = Offset(w * 0.69f, h * 0.69f),
        )

        val strokeWidth = 2.dp.toPx()

        // Cabinet dimensions
        val cabWidth = w * 0.44f
        val cabHeight = h * 0.50f
        val cabLeft = (w - cabWidth) / 2f
        val cabTop = h * 0.24f
        val cabRight = cabLeft + cabWidth
        val cabBottom = cabTop + cabHeight

        // Cabinet feet (angled slightly outward)
        val footWidth = 8.dp.toPx()
        val footHeight = 8.dp.toPx()
        val footInset = 7.dp.toPx()

        // Left foot
        val leftFootPath =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(cabLeft + footInset, cabBottom)
                lineTo(cabLeft + footInset - 2.5.dp.toPx(), cabBottom + footHeight)
                lineTo(cabLeft + footInset + footWidth - 2.5.dp.toPx(), cabBottom + footHeight)
                lineTo(cabLeft + footInset + footWidth, cabBottom)
                close()
            }
        drawPath(leftFootPath, color = cabinetFill)
        drawPath(
            leftFootPath,
            color = outlineColor,
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round),
        )

        // Right foot
        val rightFootPath =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(cabRight - footInset - footWidth, cabBottom)
                lineTo(cabRight - footInset - footWidth + 2.5.dp.toPx(), cabBottom + footHeight)
                lineTo(cabRight - footInset + 2.5.dp.toPx(), cabBottom + footHeight)
                lineTo(cabRight - footInset, cabBottom)
                close()
            }
        drawPath(rightFootPath, color = cabinetFill)
        drawPath(
            rightFootPath,
            color = outlineColor,
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round),
        )

        // Cabinet main body
        val bodyRect =
            androidx.compose.ui.geometry.RoundRect(
                left = cabLeft,
                top = cabTop + 7.dp.toPx(),
                right = cabRight,
                bottom = cabBottom,
                bottomLeftCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                bottomRightCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                topLeftCornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                topRightCornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        val bodyPath =
            androidx.compose.ui.graphics
                .Path()
                .apply { addRoundRect(bodyRect) }
        drawPath(bodyPath, color = cabinetFill)
        drawPath(bodyPath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Cabinet top lid (slightly overhanging body)
        val lidOverhang = 3.dp.toPx()
        val lidHeight = 7.dp.toPx()
        val lidRect =
            androidx.compose.ui.geometry.RoundRect(
                left = cabLeft - lidOverhang,
                top = cabTop,
                right = cabRight + lidOverhang,
                bottom = cabTop + lidHeight,
                topLeftCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                topRightCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                bottomLeftCornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                bottomRightCornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        val lidPath =
            androidx.compose.ui.graphics
                .Path()
                .apply { addRoundRect(lidRect) }
        drawPath(lidPath, color = cabinetFill)
        drawPath(lidPath, color = outlineColor, style = Stroke(width = strokeWidth))

        // 3 Drawers:
        val drawersTop = cabTop + lidHeight
        val drawersTotalHeight = cabBottom - drawersTop
        val drawerHeight = drawersTotalHeight / 3f

        // 2 Separator lines
        val line1Y = drawersTop + drawerHeight
        val line2Y = drawersTop + 2 * drawerHeight
        drawLine(
            color = outlineColor,
            start = Offset(cabLeft, line1Y),
            end = Offset(cabRight, line1Y),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = outlineColor,
            start = Offset(cabLeft, line2Y),
            end = Offset(cabRight, line2Y),
            strokeWidth = strokeWidth,
        )

        // Drawer handles (centered horizontal pill)
        val handleWidth = cabWidth * 0.32f
        val handleStroke = 3.dp.toPx()
        val handleLeft = (w - handleWidth) / 2f
        val handleRight = handleLeft + handleWidth

        for (i in 0..2) {
            val drawerCenterY = drawersTop + (i + 0.5f) * drawerHeight
            drawLine(
                color = outlineColor,
                start = Offset(handleLeft, drawerCenterY),
                end = Offset(handleRight, drawerCenterY),
                strokeWidth = handleStroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun TrashCanIllustration(
    modifier: Modifier = Modifier,
    outlineColor: Color = Color(0xFFAAA3B9),
    circleColor: Color = Color(0xFFF2E8F7),
    canFill: Color = Color.White,
) {
    Canvas(modifier = modifier.size(160.dp)) {
        val w = size.width
        val h = size.height

        // 1. Soft circular decorative shapes
        drawCircle(
            color = circleColor,
            radius = w * 0.22f,
            center = Offset(w * 0.35f, h * 0.38f),
        )
        drawCircle(
            color = circleColor,
            radius = w * 0.14f,
            center = Offset(w * 0.69f, h * 0.69f),
        )

        val strokeWidth = 2.dp.toPx()

        // Can dimensions
        val canTopWidth = w * 0.42f
        val canBottomWidth = w * 0.34f
        val canHeight = h * 0.44f
        val canTop = h * 0.32f
        val canBottom = canTop + canHeight

        val canTopLeft = (w - canTopWidth) / 2f
        val canTopRight = canTopLeft + canTopWidth
        val canBottomLeft = (w - canBottomWidth) / 2f
        val canBottomRight = canBottomLeft + canBottomWidth

        // Can body path (tapered downwards)
        val bodyPath =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(canTopLeft, canTop)
                lineTo(canBottomLeft, canBottom - 4.dp.toPx())
                quadraticTo(canBottomLeft, canBottom, canBottomLeft + 4.dp.toPx(), canBottom)
                lineTo(canBottomRight - 4.dp.toPx(), canBottom)
                quadraticTo(canBottomRight, canBottom, canBottomRight, canBottom - 4.dp.toPx())
                lineTo(canTopRight, canTop)
                close()
            }
        drawPath(bodyPath, color = canFill)
        drawPath(bodyPath, color = outlineColor, style = Stroke(width = strokeWidth, join = StrokeJoin.Round))

        // Can lid
        val lidOverhang = 4.dp.toPx()
        val lidHeight = 6.dp.toPx()
        val lidTop = canTop - lidHeight
        val lidRect =
            androidx.compose.ui.geometry.RoundRect(
                left = canTopLeft - lidOverhang,
                top = lidTop,
                right = canTopRight + lidOverhang,
                bottom = canTop,
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        val lidPath =
            androidx.compose.ui.graphics
                .Path()
                .apply { addRoundRect(lidRect) }
        drawPath(lidPath, color = canFill)
        drawPath(lidPath, color = outlineColor, style = Stroke(width = strokeWidth))

        // Lid handle loop
        val handleWidth = 14.dp.toPx()
        val handleHeight = 5.dp.toPx()
        val handlePath =
            androidx.compose.ui.graphics.Path().apply {
                moveTo((w - handleWidth) / 2f, lidTop)
                lineTo((w - handleWidth) / 2f, lidTop - handleHeight + 2.dp.toPx())
                quadraticTo((w - handleWidth) / 2f, lidTop - handleHeight, (w - handleWidth) / 2f + 2.dp.toPx(), lidTop - handleHeight)
                lineTo((w + handleWidth) / 2f - 2.dp.toPx(), lidTop - handleHeight)
                quadraticTo((w + handleWidth) / 2f, lidTop - handleHeight, (w + handleWidth) / 2f, lidTop - handleHeight + 2.dp.toPx())
                lineTo((w + handleWidth) / 2f, lidTop)
            }
        drawPath(handlePath, color = outlineColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

        // Vertical flutes / ribs inside can body
        val fluteTop = canTop + 8.dp.toPx()
        val fluteBottom = canBottom - 8.dp.toPx()
        val fluteSpacing = 9.dp.toPx()

        for (offset in listOf(-fluteSpacing, 0f, fluteSpacing)) {
            drawLine(
                color = outlineColor,
                start = Offset(w / 2f + offset * 0.95f, fluteTop),
                end = Offset(w / 2f + offset * 0.85f, fluteBottom),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ExpressiveEmptyBackdrop(
    modifier: Modifier,
    polygon: RoundedPolygon,
    morphTo: RoundedPolygon,
    color: Color,
) {
    val reducedMotion = LocalReducedMotion.current
    val shape: Shape =
        if (reducedMotion) {
            remember(polygon) { RoundedPolygonShape(polygon) }
        } else {
            val morph = remember(polygon, morphTo) { Morph(polygon, morphTo) }
            val transition = rememberInfiniteTransition(label = "emptyBackdropMorph")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 2_500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "emptyBackdropProgress",
            )
            remember(morph, progress) { MorphPolygonShape(morph, progress) }
        }
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(color),
    )
}
