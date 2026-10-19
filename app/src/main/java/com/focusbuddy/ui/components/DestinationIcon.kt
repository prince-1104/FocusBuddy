package com.focusbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.LocalContentColor
import com.focusbuddy.ui.Destination

/** Small original line icons, tinted by navigation selection. Labels live on the navigation items. */
@Composable
fun DestinationIcon(destination: Destination, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = Stroke(1.8f * unit, cap = StrokeCap.Round)
        when (destination) {
            Destination.Today -> {
                val home = Path().apply {
                    moveTo(3 * unit, 10 * unit); lineTo(12 * unit, 3 * unit)
                    lineTo(21 * unit, 10 * unit); lineTo(21 * unit, 21 * unit)
                    lineTo(15 * unit, 21 * unit); lineTo(15 * unit, 14 * unit)
                    lineTo(9 * unit, 14 * unit); lineTo(9 * unit, 21 * unit)
                    lineTo(3 * unit, 21 * unit); close()
                }
                drawPath(home, color, style = stroke)
            }
            Destination.Explore -> {
                drawRoundRect(color, point(3f, 5f), Size(18 * unit, 16 * unit), CornerRadius(3 * unit), style = stroke)
                drawLine(color, point(3f, 10f), point(21f, 10f), stroke.width)
                drawLine(color, point(8f, 3f), point(8f, 7f), stroke.width, StrokeCap.Round)
                drawLine(color, point(16f, 3f), point(16f, 7f), stroke.width, StrokeCap.Round)
                drawCircle(color, 1.5f * unit, point(8f, 15f))
                drawCircle(color, 1.5f * unit, point(16f, 15f))
            }
            Destination.Focus -> {
                drawCircle(color, 9 * unit, point(12f, 13f), style = stroke)
                drawLine(color, point(12f, 13f), point(12f, 8f), stroke.width, StrokeCap.Round)
                drawLine(color, point(9f, 1f), point(15f, 1f), stroke.width, StrokeCap.Round)
            }
            Destination.Mascot -> {
                drawOval(color, point(5f, 1f), Size(5 * unit, 11 * unit), style = stroke)
                drawOval(color, point(14f, 1f), Size(5 * unit, 11 * unit), style = stroke)
                drawOval(color, point(3f, 9f), Size(18 * unit, 13 * unit), style = stroke)
                drawCircle(color, unit, point(8f, 15f)); drawCircle(color, unit, point(16f, 15f))
            }
            Destination.Profile -> {
                drawCircle(color, 4 * unit, point(12f, 7f), style = stroke)
                drawArc(color, 180f, 180f, false, point(3f, 13f), Size(18 * unit, 16 * unit), style = stroke)
            }
        }
    }
}
