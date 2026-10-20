package three.haddad.smartnotas.ui.calendar.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Line-art Birthday Cake illustration accurately reproducing the reference image:
 * - Horizontal base pedestal line.
 * - Rectangular cake body with rounded upper corners.
 * - Interior wavy frosting line across the center.
 * - Three vertical candles sticking out of the top.
 * - Three teardrop flame outlines above the candles.
 */
@Composable
fun BirthdayCakeIllustration(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokePx = strokeWidth.toPx()
        val strokeStyle =
            Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

        // 1. Pedestal / plate line at bottom
        val plateY = h * 0.90f
        val plateLeft = w * 0.12f
        val plateRight = w * 0.88f
        drawLine(
            color = tint,
            start =
                androidx.compose.ui.geometry
                    .Offset(plateLeft, plateY),
            end =
                androidx.compose.ui.geometry
                    .Offset(plateRight, plateY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round,
        )

        // 2. Cake body
        val cakeLeft = w * 0.18f
        val cakeRight = w * 0.82f
        val cakeTop = h * 0.46f
        val cakeBottom = plateY
        val cornerRadius = 4.dp.toPx()

        val cakePath =
            Path().apply {
                moveTo(cakeLeft, cakeBottom)
                lineTo(cakeLeft, cakeTop + cornerRadius)
                quadraticTo(cakeLeft, cakeTop, cakeLeft + cornerRadius, cakeTop)
                lineTo(cakeRight - cornerRadius, cakeTop)
                quadraticTo(cakeRight, cakeTop, cakeRight, cakeTop + cornerRadius)
                lineTo(cakeRight, cakeBottom)
            }
        drawPath(
            path = cakePath,
            color = tint,
            style = strokeStyle,
        )

        // 3. Wavy frosting line across middle
        val waveY = h * 0.68f
        val wavePath =
            Path().apply {
                moveTo(cakeLeft, waveY)
                val segmentWidth = (cakeRight - cakeLeft) / 3f
                val waveHeight = 3.5f.dp.toPx()
                for (i in 0 until 3) {
                    val startX = cakeLeft + i * segmentWidth
                    val midX = startX + segmentWidth * 0.5f
                    val endX = startX + segmentWidth
                    quadraticTo(
                        midX,
                        waveY + waveHeight,
                        endX,
                        waveY,
                    )
                }
            }
        drawPath(
            path = wavePath,
            color = tint,
            style = strokeStyle,
        )

        // 4. Three candles & flames
        val candleXPositions =
            floatArrayOf(
                w * 0.32f, // Left candle
                w * 0.50f, // Center candle
                w * 0.68f, // Right candle
            )
        val candleTop = h * 0.30f
        val candleBottom = cakeTop

        for (candleX in candleXPositions) {
            // Candle stick
            drawLine(
                color = tint,
                start =
                    androidx.compose.ui.geometry
                        .Offset(candleX, candleBottom),
                end =
                    androidx.compose.ui.geometry
                        .Offset(candleX, candleTop),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )

            // Teardrop flame above candle
            val flameWidth = 3.2f.dp.toPx()
            val flameHeight = 8.5f.dp.toPx()
            val flameBaseY = candleTop - 2.5f.dp.toPx()
            val flameTipY = flameBaseY - flameHeight

            val flamePath =
                Path().apply {
                    moveTo(candleX, flameTipY)
                    cubicTo(
                        candleX + flameWidth,
                        flameTipY + flameHeight * 0.45f,
                        candleX + flameWidth,
                        flameBaseY,
                        candleX,
                        flameBaseY,
                    )
                    cubicTo(
                        candleX - flameWidth,
                        flameBaseY,
                        candleX - flameWidth,
                        flameTipY + flameHeight * 0.45f,
                        candleX,
                        flameTipY,
                    )
                    close()
                }
            drawPath(
                path = flamePath,
                color = tint,
                style = strokeStyle,
            )
        }
    }
}
