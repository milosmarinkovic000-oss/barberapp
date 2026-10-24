package rs.termin.app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import rs.termin.app.core.designsystem.TerminTheme

/** Ilustracije za prazna stanja, crtane u bojama teme (rade u svetloj i tamnoj temi). */
enum class Illustration { Calendar, Search, Inbox, Scissors, Heart, Star, NoConnection, Chart, Team, Map, Waitlist, Lock }

@Composable
fun IllustrationImage(illustration: Illustration, modifier: Modifier = Modifier) {
    val colors = TerminTheme.colors
    val soft = colors.accentSoft
    val line = colors.accentText
    val muted = colors.surface
    Canvas(modifier.clearAndSetSemantics { }) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(soft, radius = s * 0.46f, center = center)
        drawCircle(muted, radius = s * 0.06f, center = Offset(s * 0.82f, s * 0.2f))
        drawCircle(soft.copy(alpha = 0.6f), radius = s * 0.04f, center = Offset(s * 0.15f, s * 0.78f))
        when (illustration) {
            Illustration.Calendar -> calendar(s, line, muted, stroke)
            Illustration.Search -> search(s, line, muted, stroke)
            Illustration.Inbox -> inbox(s, line, muted, stroke)
            Illustration.Scissors -> scissors(s, line, stroke)
            Illustration.Heart -> heart(s, line, muted, stroke)
            Illustration.Star -> star(s, line, muted, stroke)
            Illustration.NoConnection -> noConnection(s, line, stroke)
            Illustration.Chart -> chart(s, line, muted, stroke)
            Illustration.Team -> team(s, line, muted, stroke)
            Illustration.Map -> map(s, line, muted, stroke)
            Illustration.Waitlist -> hourglass(s, line, muted, stroke)
            Illustration.Lock -> lock(s, line, muted, stroke)
        }
    }
}

private fun DrawScope.calendar(s: Float, line: Color, fill: Color, stroke: Stroke) {
    val topLeft = Offset(s * 0.27f, s * 0.3f)
    val size = Size(s * 0.46f, s * 0.42f)
    drawRoundRect(fill, topLeft, size, CornerRadius(s * 0.05f))
    drawRoundRect(line, topLeft, size, CornerRadius(s * 0.05f), style = stroke)
    drawLine(line, Offset(s * 0.27f, s * 0.41f), Offset(s * 0.73f, s * 0.41f), stroke.width)
    drawLine(line, Offset(s * 0.37f, s * 0.25f), Offset(s * 0.37f, s * 0.33f), stroke.width, StrokeCap.Round)
    drawLine(line, Offset(s * 0.63f, s * 0.25f), Offset(s * 0.63f, s * 0.33f), stroke.width, StrokeCap.Round)
    for (row in 0..1) for (col in 0..2) {
        drawCircle(line, s * 0.022f, Offset(s * (0.38f + col * 0.12f), s * (0.52f + row * 0.1f)))
    }
}

private fun DrawScope.search(s: Float, line: Color, fill: Color, stroke: Stroke) {
    drawCircle(fill, s * 0.15f, Offset(s * 0.45f, s * 0.45f))
    drawCircle(line, s * 0.15f, Offset(s * 0.45f, s * 0.45f), style = stroke)
    drawLine(line, Offset(s * 0.56f, s * 0.56f), Offset(s * 0.7f, s * 0.7f), stroke.width * 1.4f, StrokeCap.Round)
}

private fun DrawScope.inbox(s: Float, line: Color, fill: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(s * 0.26f, s * 0.52f)
        lineTo(s * 0.36f, s * 0.32f)
        lineTo(s * 0.64f, s * 0.32f)
        lineTo(s * 0.74f, s * 0.52f)
        lineTo(s * 0.74f, s * 0.7f)
        lineTo(s * 0.26f, s * 0.7f)
        close()
    }
    drawPath(path, fill)
    drawPath(path, line, style = stroke)
    val tray = Path().apply {
        moveTo(s * 0.26f, s * 0.52f)
        lineTo(s * 0.42f, s * 0.52f)
        lineTo(s * 0.46f, s * 0.58f)
        lineTo(s * 0.54f, s * 0.58f)
        lineTo(s * 0.58f, s * 0.52f)
        lineTo(s * 0.74f, s * 0.52f)
    }
    drawPath(tray, line, style = stroke)
}

private fun DrawScope.scissors(s: Float, line: Color, stroke: Stroke) {
    drawCircle(line, s * 0.07f, Offset(s * 0.36f, s * 0.64f), style = stroke)
    drawCircle(line, s * 0.07f, Offset(s * 0.64f, s * 0.64f), style = stroke)
    drawLine(line, Offset(s * 0.4f, s * 0.58f), Offset(s * 0.62f, s * 0.28f), stroke.width, StrokeCap.Round)
    drawLine(line, Offset(s * 0.6f, s * 0.58f), Offset(s * 0.38f, s * 0.28f), stroke.width, StrokeCap.Round)
}

private fun heartPath(s: Float): Path = Path().apply {
    moveTo(s * 0.5f, s * 0.7f)
    cubicTo(s * 0.2f, s * 0.5f, s * 0.26f, s * 0.28f, s * 0.4f, s * 0.3f)
    cubicTo(s * 0.46f, s * 0.31f, s * 0.49f, s * 0.35f, s * 0.5f, s * 0.38f)
    cubicTo(s * 0.51f, s * 0.35f, s * 0.54f, s * 0.31f, s * 0.6f, s * 0.3f)
    cubicTo(s * 0.74f, s * 0.28f, s * 0.8f, s * 0.5f, s * 0.5f, s * 0.7f)
    close()
}

private fun DrawScope.heart(s: Float, line: Color, fill: Color, stroke: Stroke) {
    val path = heartPath(s)
    drawPath(path, fill)
    drawPath(path, line, style = stroke)
}

private fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else r * 0.45f
        val angle = Math.toRadians((i * 36.0) - 90.0)
        val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
        val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.star(s: Float, line: Color, fill: Color, stroke: Stroke) {
    val path = starPath(s * 0.5f, s * 0.5f, s * 0.22f)
    drawPath(path, fill)
    drawPath(path, line, style = stroke)
}

private fun DrawScope.noConnection(s: Float, line: Color, stroke: Stroke) {
    for (i in 0..2) {
        val r = s * (0.1f + i * 0.08f)
        drawArc(
            line,
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(s * 0.5f - r, s * 0.6f - r),
            size = Size(r * 2, r * 2),
            style = stroke,
        )
    }
    drawCircle(line, s * 0.03f, Offset(s * 0.5f, s * 0.6f))
    drawLine(line, Offset(s * 0.3f, s * 0.3f), Offset(s * 0.7f, s * 0.7f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.chart(s: Float, line: Color, fill: Color, stroke: Stroke) {
    val bars = listOf(0.18f, 0.3f, 0.24f, 0.38f)
    bars.forEachIndexed { i, h ->
        val left = s * (0.3f + i * 0.11f)
        drawRoundRect(fill, Offset(left, s * (0.7f - h)), Size(s * 0.07f, s * h), CornerRadius(s * 0.02f))
        drawRoundRect(line, Offset(left, s * (0.7f - h)), Size(s * 0.07f, s * h), CornerRadius(s * 0.02f), style = stroke)
    }
    drawLine(line, Offset(s * 0.26f, s * 0.72f), Offset(s * 0.76f, s * 0.72f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.team(s: Float, line: Color, fill: Color, stroke: Stroke) {
    listOf(0.38f to 0.42f, 0.62f to 0.42f).forEach { (x, y) ->
        drawCircle(fill, s * 0.08f, Offset(s * x, s * y))
        drawCircle(line, s * 0.08f, Offset(s * x, s * y), style = stroke)
        drawArc(line, 200f, 140f, false, Offset(s * (x - 0.13f), s * (y + 0.12f)), Size(s * 0.26f, s * 0.26f), style = stroke)
    }
}

private fun DrawScope.map(s: Float, line: Color, fill: Color, stroke: Stroke) {
    val pin = Path().apply {
        moveTo(s * 0.5f, s * 0.72f)
        cubicTo(s * 0.36f, s * 0.55f, s * 0.34f, s * 0.48f, s * 0.34f, s * 0.42f)
        cubicTo(s * 0.34f, s * 0.32f, s * 0.42f, s * 0.26f, s * 0.5f, s * 0.26f)
        cubicTo(s * 0.58f, s * 0.26f, s * 0.66f, s * 0.32f, s * 0.66f, s * 0.42f)
        cubicTo(s * 0.66f, s * 0.48f, s * 0.64f, s * 0.55f, s * 0.5f, s * 0.72f)
        close()
    }
    drawPath(pin, fill)
    drawPath(pin, line, style = stroke)
    drawCircle(line, s * 0.05f, Offset(s * 0.5f, s * 0.42f), style = stroke)
}

private fun DrawScope.hourglass(s: Float, line: Color, fill: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(s * 0.36f, s * 0.28f)
        lineTo(s * 0.64f, s * 0.28f)
        lineTo(s * 0.5f, s * 0.5f)
        lineTo(s * 0.64f, s * 0.72f)
        lineTo(s * 0.36f, s * 0.72f)
        lineTo(s * 0.5f, s * 0.5f)
        close()
    }
    drawPath(path, fill)
    drawPath(path, line, style = stroke)
}

private fun DrawScope.lock(s: Float, line: Color, fill: Color, stroke: Stroke) {
    drawRoundRect(fill, Offset(s * 0.34f, s * 0.46f), Size(s * 0.32f, s * 0.26f), CornerRadius(s * 0.04f))
    drawRoundRect(line, Offset(s * 0.34f, s * 0.46f), Size(s * 0.32f, s * 0.26f), CornerRadius(s * 0.04f), style = stroke)
    drawArc(line, 180f, 180f, false, Offset(s * 0.4f, s * 0.3f), Size(s * 0.2f, s * 0.3f), style = stroke)
}
