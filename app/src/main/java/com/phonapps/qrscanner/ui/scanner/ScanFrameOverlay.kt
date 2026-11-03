package com.phonapps.qrscanner.ui.scanner

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Premium viewfinder overlay: dimmed surround, elegant corner brackets,
 * a slow travelling scan line and an accent flash on detection.
 */
@Composable
fun ScanFrameOverlay(
    success: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val scanLine by rememberInfiniteTransition(label = "scanline").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val frameFraction = 0.68f
        val frameSize = minOf(maxWidth, maxHeight) * frameFraction
        Canvas(modifier = Modifier.fillMaxSize()) {
            val side = frameSize.toPx()
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            val cornerLen = side * 0.16f
            val stroke = 5.dp.toPx()
            val radius = 22.dp.toPx()
            val dim = Color.Black.copy(alpha = 0.55f)

            // Dim the surround with four rects (no blend-mode tricks needed).
            drawRect(dim, Offset.Zero, Size(size.width, top)) // above
            drawRect(dim, Offset(0f, top + side), Size(size.width, size.height - top - side)) // below
            drawRect(dim, Offset(0f, top), Size(left, side)) // left
            drawRect(dim, Offset(left + side, top), Size(size.width - left - side, side)) // right

            // Soft frame edge.
            drawRoundRect(
                color = Color.White.copy(alpha = if (success) 0.0f else 0.18f),
                topLeft = Offset(left, top),
                size = Size(side, side),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Corner brackets; flash the accent color on a hit.
            val bracketColor = if (success) accent else Color.White
            fun bracket(start: Offset, hDir: Float, vDir: Float) {
                drawLine(bracketColor, start, start + Offset(cornerLen * hDir, 0f), stroke)
                drawLine(bracketColor, start, start + Offset(0f, cornerLen * vDir), stroke)
            }
            bracket(Offset(left, top), 1f, 1f)
            bracket(Offset(left + side, top), -1f, 1f)
            bracket(Offset(left, top + side), 1f, -1f)
            bracket(Offset(left + side, top + side), -1f, -1f)

            if (success) {
                drawRoundRect(
                    color = accent.copy(alpha = 0.25f),
                    topLeft = Offset(left, top),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Travelling scan line.
            val lineY = top + 12.dp.toPx() + (side - 24.dp.toPx()) * scanLine
            drawLine(
                color = accent.copy(alpha = 0.9f),
                start = Offset(left + 20.dp.toPx(), lineY),
                end = Offset(left + side - 20.dp.toPx(), lineY),
                strokeWidth = 2.5.dp.toPx()
            )
        }
    }
}
