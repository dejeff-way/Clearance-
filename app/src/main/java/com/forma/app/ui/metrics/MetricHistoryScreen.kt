package com.forma.app.ui.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.forma.app.data.prefs.AppSettings
import com.forma.app.health.HcDataType
import com.forma.app.health.HealthSyncer
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.BalanceBars
import com.forma.app.ui.components.ChartPoint
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.components.TrendChart
import com.forma.app.ui.nav.Routes
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Inter
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

private enum class Range(val label: String, val days: Int, val weeks: Int) { D7("7 days", 7, 4), D30("30 days", 30, 8), D90("90 days", 90, 13) }

@Composable
fun MetricHistoryScreen(nav: NavController, metricName: String) {
    val container = LocalContainer.current
    val metric = Metric.entries.firstOrNull { it.name == metricName } ?: Metric.STEPS
    val model by container.energyModel.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val meta by container.syncMeta.collectAsStateWithLifecycle(null)
    val m = model ?: return Box(Modifier.fillMaxSize())
    val c = Forma.colors
    var range by rememberSaveable { mutableStateOf(Range.D30) }
    val unit = metric.unit(settings)
    val granted = meta?.grantedPermissions.orEmpty().split(',').toSet()
    val hcConnected = granted.any { it.isNotBlank() }
    val permissionMissing = metric.hcType != null && hcConnected && metric.hcType.name !in granted

    val daily = if (metric == Metric.WORKOUTS) emptyList() else MetricValues.series(metric, m, range.days, settings)
    val weekly = MetricValues.weekly(metric, m, range.weeks, settings)
    val withData = daily.mapNotNull { it.second }
    val today = m.s.today
    val todayValue = daily.lastOrNull()?.second

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("metric_${metric.name}")) {
        BackBar(metric.label, { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            if (permissionMissing) {
                Note("FORMA doesn't have permission to read ${metric.hcType!!.label.lowercase()}. Allow it in Settings → Health Connect.", Tone.WARN)
                TextAction("Open Health Connect settings", { nav.navigate(Routes.HEALTH) })
                Spacer(Modifier.height(Space.s))
            } else if (metric.hcType != null && !hcConnected) {
                Note("Health Connect isn't connected, so there's no ${metric.label.lowercase()} data.", Tone.NEUTRAL)
                Spacer(Modifier.height(Space.s))
            }

            Segmented(Range.entries, range, { it.label }, { range = it }, Modifier.fillMaxWidth().testTag("metric_range"))
            Spacer(Modifier.height(Space.l))

            FormaCard {
                Row {
                    if (metric == Metric.WORKOUTS) {
                        StatBlock("This week", weekly.lastOrNull()?.second?.let { metric.format(it) } ?: "—", unit, Modifier.weight(1f), style = Forma.type.numberLarge)
                        StatBlock("Weekly average", weekly.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()?.let { Fmt.one(it) } ?: "—", null, Modifier.weight(1f), style = Forma.type.numberLarge)
                    } else {
                        StatBlock(if (metric in listOf(Metric.SLEEP)) "Last night" else "Today", todayValue?.let { metric.format(it) } ?: "—", unit, Modifier.weight(1f), style = Forma.type.numberLarge)
                        StatBlock("${range.label} average", withData.takeIf { it.isNotEmpty() }?.average()?.let { metric.format(it) } ?: "—", unit, Modifier.weight(1f), style = Forma.type.numberLarge)
                    }
                }
                if (metric != Metric.WORKOUTS) {
                    Spacer(Modifier.height(Space.m))
                    Row {
                        StatBlock("Lowest", withData.minOrNull()?.let { metric.format(it) } ?: "—", null, Modifier.weight(1f))
                        StatBlock("Highest", withData.maxOrNull()?.let { metric.format(it) } ?: "—", null, Modifier.weight(1f))
                        StatBlock("Days with data", "${withData.size}/${range.days}", null, Modifier.weight(1f))
                    }
                }
                if (metric == Metric.STEPS) {
                    val goal = m.s.profile.stepGoal
                    Spacer(Modifier.height(Space.m)); Hairline(); Spacer(Modifier.height(Space.s))
                    Text(
                        "Goal ${Fmt.int(goal)} · met on ${withData.count { it >= goal }} of ${withData.size} days" +
                            (todayValue?.let { if (it < goal) " · ${Fmt.int(goal - it)} to go today" else " · reached today" } ?: ""),
                        style = Forma.type.caption, color = c.textSecondary,
                    )
                }
                if (todayValue == null && metric.hcType != null && !permissionMissing && hcConnected && metric != Metric.SLEEP) {
                    Text("No reading yet today — this updates after your device syncs to Health Connect.", style = Forma.type.caption, color = c.textTertiary,
                        modifier = Modifier.padding(top = Space.s))
                }
            }

            Spacer(Modifier.height(Space.m))
            FormaCard {
                when {
                    metric == Metric.WORKOUTS -> ValueBars(weekly, weekly = true)
                    withData.isEmpty() -> Text("No data in this period.", style = Forma.type.bodySmall, color = c.textSecondary,
                        modifier = Modifier.padding(vertical = Space.xl))
                    metric.chart == ChartKind.BALANCE -> BalanceBars(daily.map { it.first to it.second?.toInt() }, average = withData.average().toInt())
                    metric.chart == ChartKind.LINE -> {
                        val pts = daily.mapNotNull { (d, v) -> v?.let { ChartPoint(d, it) } }
                        val avg = pts.mapIndexed { i, p -> ChartPoint(p.date, pts.filter { !it.date.isAfter(p.date) && it.date.isAfter(p.date.minusDays(7)) }.map { it.value }.average()) }
                        TrendChart(raw = pts, trend = avg, height = 190.dp)
                    }
                    else -> ValueBars(daily, goal = if (metric == Metric.STEPS) m.s.profile.stepGoal.toDouble() else null)
                }
                Spacer(Modifier.height(Space.s))
                Text(
                    when (metric.chart) {
                        ChartKind.LINE -> "Dots are daily values; the line is the 7-day average."
                        ChartKind.BALANCE -> "Up = deficit, down = surplus. Dashed line = average."
                        ChartKind.BARS -> if (metric == Metric.WORKOUTS) "Sessions per week." else "Days without data are left blank, not shown as zero."
                    },
                    style = Forma.type.caption, color = c.textTertiary,
                )
            }

            // Trend: last 30 days vs the 30 before, when there's enough data.
            if (metric != Metric.WORKOUTS) {
                val last30 = MetricValues.series(metric, m, 60, settings)
                val recent = last30.takeLast(30).mapNotNull { it.second }
                val prior = last30.take(30).mapNotNull { it.second }
                if (recent.size >= 7 && prior.size >= 7) {
                    val delta = recent.average() - prior.average()
                    val pct = if (prior.average() != 0.0) delta / abs(prior.average()) * 100 else 0.0
                    val good = metric.higherIsBetter?.let { if (it) delta >= 0 else delta <= 0 }
                    Spacer(Modifier.height(Space.m))
                    Note("30-day trend: ${if (delta >= 0) "up" else "down"} ${metric.format(abs(delta))} $unit (${Fmt.signedOne(pct)}%) vs the previous 30 days.",
                        if (good == null || abs(pct) < 2) Tone.NEUTRAL else if (good) Tone.ACCENT else Tone.WARN)
                }
            }

            SectionHeader("Weekly averages")
            FormaCard {
                weekly.reversed().forEachIndexed { i, (ws, v) ->
                    if (i > 0) Hairline()
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                        Text("Week of ${Fmt.shortDate(ws)}", style = Forma.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text(v?.let { "${if (metric == Metric.WORKOUTS) Fmt.int(it) else metric.format(it)} $unit" } ?: "—", style = Forma.type.numberSmall,
                            color = if (v == null) c.textTertiary else c.text)
                    }
                }
            }

            if (metric == Metric.SLEEP) SleepStages(m, range.days)

            SectionHeader("About this metric")
            Text(metric.explanation, style = Forma.type.bodySmall, color = c.textSecondary)
            metric.hcType?.let { t ->
                val sources = (0 until range.days).flatMap { HealthSyncer.decodeSources(m.s.energy[today.minusDays(it.toLong()).toEpochDay()]?.sources)[t].orEmpty() }.toSet()
                if (sources.isNotEmpty()) {
                    Spacer(Modifier.height(Space.xs))
                    Text("Source: ${sources.joinToString { appLabel(it) }}", style = Forma.type.caption, color = c.textTertiary)
                }
            }
            if (metric == Metric.RESTING_HR || metric == Metric.HRV || metric == Metric.SPO2) {
                Spacer(Modifier.height(Space.xs))
                Text("Wearable readings are estimates and not medical measurements.", style = Forma.type.caption, color = c.textTertiary)
            }
        }
    }
}

@Composable
private fun SleepStages(m: com.forma.app.data.repo.EnergyModel, days: Int) {
    val c = Forma.colors
    val rows = (0 until days).mapNotNull { m.s.energy[m.s.today.minusDays(it.toLong()).toEpochDay()] }.filter { it.sleepDeepMin != null || it.sleepRemMin != null || it.sleepLightMin != null }
    SectionHeader("Sleep stages")
    FormaCard {
        if (rows.isEmpty()) Text("Your device hasn't shared sleep stages with Health Connect for this period.", style = Forma.type.bodySmall, color = c.textSecondary)
        else {
            fun avg(f: (com.forma.app.data.db.DailyEnergyEntity) -> Double?) = rows.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
            Row {
                StatBlock("Deep", avg { it.sleepDeepMin }?.let { "${(it / 60).toInt()}h ${(it % 60).toInt()}m" } ?: "—", null, Modifier.weight(1f))
                StatBlock("REM", avg { it.sleepRemMin }?.let { "${(it / 60).toInt()}h ${(it % 60).toInt()}m" } ?: "—", null, Modifier.weight(1f))
                StatBlock("Light", avg { it.sleepLightMin }?.let { "${(it / 60).toInt()}h ${(it % 60).toInt()}m" } ?: "—", null, Modifier.weight(1f))
                StatBlock("Awake", avg { it.sleepAwakeMin }?.let { "${it.toInt()}m" } ?: "—", null, Modifier.weight(1f))
            }
            Text("Nightly averages over ${rows.size} night${if (rows.size == 1) "" else "s"} with stage data.", style = Forma.type.caption, color = c.textTertiary,
                modifier = Modifier.padding(top = Space.s))
        }
    }
}

/** Friendly names for common Health Connect data sources. */
fun appLabel(pkg: String): String = when (pkg) {
    "com.fitbit.FitbitMobile" -> "Fitbit"
    "com.google.android.apps.fitness" -> "Google Fit"
    "com.google.android.apps.healthdata" -> "Health Connect"
    "com.google.android.apps.wear.companion" -> "Pixel Watch"
    "com.sec.android.app.shealth" -> "Samsung Health"
    "com.samsung.android.wear.shealth" -> "Samsung Health (watch)"
    "com.ouraring.oura" -> "Oura"
    "com.garmin.android.apps.connectmobile" -> "Garmin Connect"
    "com.whoop.android" -> "WHOOP"
    "com.strava" -> "Strava"
    "android" -> "Android"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() } + " ($pkg)"
}

/** Thin daily (or weekly) bars; missing values leave a gap; optional dashed goal line. */
@Composable
fun ValueBars(values: List<Pair<LocalDate, Double?>>, modifier: Modifier = Modifier, height: Dp = 160.dp, goal: Double? = null, weekly: Boolean = false) {
    val c = Forma.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = c.textTertiary)
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = "Bar chart" }) {
        if (values.isEmpty()) return@Canvas
        val top = max(goal ?: 0.0, values.mapNotNull { it.second }.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
        val bottomPad = 16.dp.toPx()
        val h = size.height - bottomPad
        val slot = size.width / values.size
        val bw = (slot * 0.6f).coerceAtMost(16.dp.toPx())
        drawLine(c.lineStrong, Offset(0f, h), Offset(size.width, h), 1f)
        values.forEachIndexed { i, (_, v) ->
            if (v == null) return@forEachIndexed
            val bh = (v / top * (h - 6.dp.toPx())).toFloat()
            val x = slot * i + (slot - bw) / 2
            drawRoundRect(if (goal != null && v >= goal) c.accent else c.accent.copy(alpha = if (goal != null) 0.5f else 0.85f),
                Offset(x, h - bh), Size(bw, bh.coerceAtLeast(2f)), CornerRadius(bw / 3))
        }
        if (goal != null) {
            val y = (h - goal / top * (h - 6.dp.toPx())).toFloat()
            drawLine(c.textSecondary, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        val first = measurer.measure(if (weekly) "Wk ${Fmt.shortDate(values.first().first)}" else Fmt.shortDate(values.first().first), labelStyle)
        drawText(first, topLeft = Offset(0f, h + 3.dp.toPx()))
        val last = measurer.measure(Fmt.shortDate(values.last().first), labelStyle)
        drawText(last, topLeft = Offset(size.width - last.size.width, h + 3.dp.toPx()))
    }
}

