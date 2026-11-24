package com.forma.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Inter
import com.forma.app.util.Fmt
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Calorie ring: a 270° arc. The filled portion is consumed ÷ budget; anything over budget is drawn in the
 * negative colour as a second lap so overages are obvious but not alarming.
 */
@Composable
fun CalorieRing(
    consumed: Double,
    budget: Int,
    modifier: Modifier = Modifier,
    diameter: Dp = 208.dp,
    stroke: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Forma.colors
    val target = if (budget <= 0) 0f else (consumed / budget).toFloat()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, tween(700, easing = FastOutSlowInEasing)) }
    Box(modifier.size(diameter).semantics {
        contentDescription = "Consumed ${Fmt.kcal(consumed)} of ${Fmt.kcal(budget)} kilocalories"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(size.width - sw, size.height - sw)
            val start = 135f
            val sweep = 270f
            drawArc(c.track, start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            val f = anim.value
            val main = min(f, 1f)
            if (main > 0.001f) drawArc(c.accent, start, sweep * main, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (f > 1f) {
                val over = min(f - 1f, 1f)
                drawArc(c.negative, start, sweep * over, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            // Small tick marking the budget point at the end of the arc.
            if (f > 0f) {
                val tickR = (size.width - sw) / 2
                val ang = Math.toRadians((start + sweep).toDouble())
                val cx = size.width / 2 + (tickR * kotlin.math.cos(ang)).toFloat()
                val cy = size.height / 2 + (tickR * kotlin.math.sin(ang)).toFloat()
                drawCircle(c.surface, sw * 0.22f, Offset(cx, cy))
            }
        }
        content()
    }
}

data class ChartPoint(val date: LocalDate, val value: Double)

/**
 * Weight chart: raw weigh-ins as faint dots, the 7-day average as a single thin line. Three quiet gridlines.
 */
@Composable
fun TrendChart(
    raw: List<ChartPoint>,
    trend: List<ChartPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    unitLabel: String = "",
    reference: Double? = null,
) {
    val c = Forma.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = c.textTertiary)
    val progress by animateFloatAsState(1f, tween(800), label = "chart")
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = "Weight trend chart" }) {
        val all = raw + trend
        if (all.isEmpty()) return@Canvas
        val d0 = all.minOf { it.date }
        val d1 = all.maxOf { it.date }
        val span = max(1L, ChronoUnit.DAYS.between(d0, d1)).toFloat()
        var lo = all.minOf { it.value }; var hi = all.maxOf { it.value }
        if (reference != null) { lo = min(lo, reference); hi = max(hi, reference) }
        val pad = max(1.0, (hi - lo) * 0.15)
        lo -= pad; hi += pad
        val labelW = 34.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val w = size.width - labelW
        val h = size.height - bottomPad
        fun x(d: LocalDate) = ChronoUnit.DAYS.between(d0, d).toFloat() / span * w
        fun y(v: Double) = (h - ((v - lo) / (hi - lo) * h)).toFloat()

        // Gridlines + labels
        listOf(lo + (hi - lo) * 0.15, (lo + hi) / 2, hi - (hi - lo) * 0.15).forEach { v ->
            val yy = y(v)
            drawLine(c.line, Offset(0f, yy), Offset(w, yy), 1f)
            val t = measurer.measure(Fmt.one(v), labelStyle)
            drawText(t, topLeft = Offset(w + 6.dp.toPx(), yy - t.size.height / 2))
        }
        if (reference != null) {
            val yy = y(reference)
            drawLine(c.textTertiary, Offset(0f, yy), Offset(w, yy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        // Raw dots
        raw.forEach { p -> drawCircle(c.textTertiary.copy(alpha = 0.55f), 2.2.dp.toPx(), Offset(x(p.date), y(p.value))) }
        // Trend line
        if (trend.size >= 2) {
            val path = Path()
            val pts = trend.map { Offset(x(it.date), y(it.value)) }
            val n = max(2, (pts.size * progress).toInt())
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until min(n, pts.size)) {
                val p0 = pts[i - 1]; val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2
                path.cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
            drawPath(path, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(c.surface, 4.dp.toPx(), pts.last())
            drawCircle(c.accent, 4.dp.toPx(), pts.last(), style = Stroke(2.dp.toPx()))
        } else if (trend.size == 1) {
            drawCircle(c.accent, 3.dp.toPx(), Offset(x(trend[0].date), y(trend[0].value)))
        }
        // X labels
        val first = measurer.measure(Fmt.shortDate(d0), labelStyle)
        drawText(first, topLeft = Offset(0f, h + 5.dp.toPx()))
        if (d1 != d0) {
            val last = measurer.measure(Fmt.shortDate(d1), labelStyle)
            drawText(last, topLeft = Offset(w - last.size.width, h + 5.dp.toPx()))
        }
    }
}

/** Daily deficit (up, accent) / surplus (down, negative) bars with a zero line and dashed average. */
@Composable
fun BalanceBars(
    values: List<Pair<LocalDate, Int?>>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    average: Int? = null,
) {
    val c = Forma.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = c.textTertiary)
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = "Daily calorie balance chart" }) {
        if (values.isEmpty()) return@Canvas
        val maxAbs = max(500, values.mapNotNull { it.second }.maxOfOrNull { abs(it) } ?: 500).toFloat()
        val bottomPad = 16.dp.toPx()
        val h = size.height - bottomPad
        val mid = h / 2
        val slot = size.width / values.size
        val bw = min(slot * 0.55f, 14.dp.toPx())
        drawLine(c.lineStrong, Offset(0f, mid), Offset(size.width, mid), 1f)
        values.forEachIndexed { i, (_, v) ->
            val cx = slot * i + slot / 2
            if (v == null) {
                drawCircle(c.line, 1.5.dp.toPx(), Offset(cx, mid))
            } else {
                val bh = abs(v) / maxAbs * (h / 2 - 4.dp.toPx())
                val top = if (v >= 0) mid - bh else mid
                drawRoundRect(
                    if (v >= 0) c.accent else c.negative, Offset(cx - bw / 2, top), Size(bw, max(bh, 2f)),
                    androidx.compose.ui.geometry.CornerRadius(bw / 3),
                )
            }
        }
        if (average != null) {
            val yy = mid - average / maxAbs * (h / 2 - 4.dp.toPx())
            drawLine(c.textSecondary, Offset(0f, yy), Offset(size.width, yy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
        }
        val first = measurer.measure(Fmt.shortDate(values.first().first), labelStyle)
        drawText(first, topLeft = Offset(0f, h + 3.dp.toPx()))
        val last = measurer.measure(Fmt.shortDate(values.last().first), labelStyle)
        drawText(last, topLeft = Offset(size.width - last.size.width, h + 3.dp.toPx()))
    }
}

/** Minimal sparkline for the dashboard weight card. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = Forma.colors.accent) {
    val c = Forma.colors
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(c.line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
            return@Canvas
        }
        val lo = values.min(); val hi = values.max()
        val r = if (hi - lo < 0.5) 0.5 else hi - lo
        val pts = values.mapIndexed { i, v ->
            Offset(i / (values.size - 1f) * size.width, (size.height - ((v - lo) / r * size.height * 0.8 + size.height * 0.1)).toFloat())
        }
        val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(p, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 2.5.dp.toPx(), pts.last())
    }
}

/** Horizontal week of seven dots/marks (filled = done, ring = planned, faint = rest/optional). */
enum class DayMark { DONE, PLANNED, MISSED, REST, OPTIONAL, OPTIONAL_DONE }

fun DrawScope.drawDayMark(m: DayMark, center: Offset, r: Float, c: com.forma.app.ui.theme.FormaColors) {
    when (m) {
        DayMark.DONE, DayMark.OPTIONAL_DONE -> drawCircle(c.accent, r, center)
        DayMark.PLANNED -> drawCircle(c.lineStrong, r, center, style = Stroke(1.5f * density))
        DayMark.MISSED -> drawCircle(c.negative.copy(alpha = 0.5f), r, center, style = Stroke(1.5f * density))
        DayMark.REST -> drawLine(c.lineStrong, Offset(center.x - r * 0.6f, center.y), Offset(center.x + r * 0.6f, center.y), 1.5f * density)
        DayMark.OPTIONAL -> drawCircle(c.line, r, center, style = Stroke(1.2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
    }
}
