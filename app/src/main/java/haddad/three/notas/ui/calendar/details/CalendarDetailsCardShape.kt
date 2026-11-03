package haddad.three.notas.ui.calendar.details

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Custom Shape for the Main Details Card matching the reference design.
 * Features:
 * - A large rounded top-left corner.
 * - A horizontal top edge across the title area.
 * - A smooth, wide S-curve (cubic Bézier) transition with generous radius stepping down around the circular Edit FAB.
 * - A lower horizontal shelf running to the right edge with a smooth rounded corner.
 * - Vertical left/right edges and horizontal bottom edge extending through the bottom of the card.
 */
class CalendarDetailsCardShape(
    private val topStartRadius: Dp = 48.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val topStartPx = with(density) { topStartRadius.toPx() }

        // Reference geometry ratios based on 473px width reference
        val stepOffsetPx = size.width * (207f / 473f)
        val stepWidthPx = size.width * (74f / 473f)
        val stepHeightPx = size.width * (83f / 473f)
        val topEndRadiusPx = size.width * (36f / 473f)

        val stepStart = size.width - stepOffsetPx
        val stepEnd = stepStart + stepWidthPx

        val kappa = 0.55228475f

        val path =
            Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, topStartPx)
                // Top-left round corner
                cubicTo(
                    0f,
                    topStartPx * (1f - kappa),
                    topStartPx * (1f - kappa),
                    0f,
                    topStartPx,
                    0f,
                )
                // High plateau across title area
                lineTo(stepStart, 0f)
                // Smooth S-curve transition stepping down around the FAB notch with generous radius
                cubicTo(
                    stepStart + stepWidthPx * 0.68f,
                    0f,
                    stepStart + stepWidthPx * 0.38f,
                    stepHeightPx,
                    stepEnd,
                    stepHeightPx,
                )
                // Lower shelf extending rightwards, then smoothly rounding down into the right edge
                val shelfEndX = (size.width - topEndRadiusPx).coerceAtLeast(stepEnd)
                lineTo(shelfEndX, stepHeightPx)
                cubicTo(
                    size.width - topEndRadiusPx * (1f - kappa),
                    stepHeightPx,
                    size.width,
                    stepHeightPx + topEndRadiusPx * (1f - kappa),
                    size.width,
                    stepHeightPx + topEndRadiusPx,
                )
                lineTo(size.width, size.height)
                close()
            }
        return Outline.Generic(path)
    }
}
