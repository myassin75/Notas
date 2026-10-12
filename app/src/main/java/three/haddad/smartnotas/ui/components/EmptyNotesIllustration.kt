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
            modifier = Modifier
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
            modifier = Modifier
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
fun EmptyArchiveIllustration(modifier: Modifier = Modifier) {
    EmptyNotesIllustration(modifier = modifier)
}

@Composable
fun EmptyTrashIllustration(modifier: Modifier = Modifier) {
    EmptyNotesIllustration(modifier = modifier)
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
