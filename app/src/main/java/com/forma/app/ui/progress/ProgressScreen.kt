package com.forma.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.db.MeasurementEntity
import com.forma.app.data.db.WeightEntity
import com.forma.app.data.db.WorkoutSessionEntity
import com.forma.app.data.prefs.AppSettings
import com.forma.app.data.repo.EnergyModel
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.BalanceBars
import com.forma.app.ui.components.ChartPoint
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.ConfirmDialog
import com.forma.app.ui.components.EmptyState
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.IconAction
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.ScreenTitle
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.components.TrendChart
import com.forma.app.ui.components.WeightSheet
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.RecommendationKind
import com.forma.engine.TrendAnalyzer
import com.forma.engine.WeightPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class ProgressState(
    val model: EnergyModel,
    val measurements: List<MeasurementEntity>,
    val workouts: List<WorkoutSessionEntity>,
    val settings: AppSettings,
)

class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    val state = combine(c.energyModel.filterNotNull(), c.body.measurements, c.workouts.sessions, c.settings.settings) { m, me, w, s ->
        ProgressState(m, me, w, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun logWeight(lb: Double) = viewModelScope.launch { runCatching { c.body.logWeight(lb) } }
    fun deleteWeight(id: Long) = viewModelScope.launch { c.body.deleteWeight(id) }
    fun addWaist(inches: Double, day: LocalDate, note: String?) = viewModelScope.launch { runCatching { c.body.addMeasurement(inches, day.toEpochDay(), note) } }
    fun deleteWaist(id: Long) = viewModelScope.launch { c.body.deleteMeasurement(id) }

    /** Applies a recommendation by changing the desired deficit, within safe bounds. Never automatic. */
    fun applyDeficitChange(delta: Int) = viewModelScope.launch {
        c.profile.update { it.copy(desiredDeficitKcal = (it.desiredDeficitKcal + delta).coerceIn(250, 1000)) }
    }
}

private enum class Range(val label: String, val days: Long?) { M1("30 days", 30), M3("90 days", 90), ALL("All", null) }

@Composable
fun ProgressScreen(nav: NavController) {
    val vm = formaViewModel { ProgressViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return Box(Modifier.fillMaxSize())
    val catalog by com.forma.app.ui.LocalContainer.current.catalog.collectAsStateWithLifecycle()
    val c = Forma.colors
    val m = s.model
    val today = m.s.today
    val unit = s.settings.weightUnit
    val ul = Fmt.weightUnit(unit)
    var range by rememberSaveable { mutableStateOf(Range.M1) }
    var weightSheet by remember { mutableStateOf(false) }
    var waistSheet by remember { mutableStateOf(false) }
    var deleteWeight by remember { mutableStateOf<WeightEntity?>(null) }
    var dismissedRec by rememberSaveable { mutableStateOf(false) }

    val points = m.s.weights.map { WeightPoint(LocalDate.ofEpochDay(it.epochDay), it.weightLb) }
    val trend = TrendAnalyzer.rolling7(points)
    val from = range.days?.let { today.minusDays(it - 1) }
    val shownTrend = trend.filter { from == null || !it.date.isBefore(from) }
    val start = m.s.profile.startWeightLb
    val current = trend.lastOrNull()?.average7
    val thisWeek = TrendAnalyzer.weeklyAverages(points).lastOrNull()?.takeIf { it.first == TrendAnalyzer.weekStart(today) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = Space.gutter).padding(bottom = Space.xxl).testTag("progress"),
    ) {
        ScreenTitle("Progress", subtitle = "Since ${Fmt.fullDate(listOfNotNull(LocalDate.ofEpochDay(m.s.profile.programStartEpochDay), points.minOfOrNull { it.date }).min())}")
        Spacer(Modifier.height(Space.l))

        // ── Weight ──────────────────────────────────────────────
        FormaCard {
            Row {
                StatBlock("Trend weight", current?.let { Fmt.weight(it, unit) } ?: "—", ul, Modifier.weight(1f), style = Forma.type.numberLarge)
                StatBlock("Since start", current?.let { Fmt.signedOne(Fmt.fromLb(it - start, unit)) } ?: "—", ul, Modifier.weight(1f),
                    style = Forma.type.numberLarge, valueColor = if (current != null && current < start) c.accent else c.text)
            }
            Spacer(Modifier.height(Space.m))
            Row {
                StatBlock("This week's average", thisWeek?.second?.let { Fmt.weight(it, unit) } ?: "—", ul, Modifier.weight(1f))
                StatBlock("Starting weight", Fmt.weight(start, unit), ul, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.l))
            Segmented(Range.entries, range, { it.label }, { range = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.l))
            if (shownTrend.isEmpty()) {
                EmptyState("No weigh-ins yet", "Log your weight or connect a scale through Health Connect.")
            } else {
                TrendChart(
                    raw = shownTrend.map { ChartPoint(it.date, Fmt.fromLb(it.raw, unit)) },
                    trend = shownTrend.map { ChartPoint(it.date, Fmt.fromLb(it.average7, unit)) },
                    unitLabel = ul,
                )
                Spacer(Modifier.height(Space.s))
                Text("Dots are individual weigh-ins; the line is the rolling 7-day average.", style = Forma.type.caption, color = c.textTertiary)
            }
            Spacer(Modifier.height(Space.m))
            SecondaryButton("Log weight", { weightSheet = true }, Modifier.fillMaxWidth().testTag("progress_log_weight"), icon = Icons.Outlined.Add)
        }

        // ── Recommendation ──────────────────────────────────────
        val window = points.filter { !it.date.isBefore(today.minusDays(27)) }
        val hist = m.history(28)
        val logged = hist.filter { m.hasFood(it.first) && it.first != today }
        val rec = TrendAnalyzer.recommend(
            window, logged.takeIf { it.isNotEmpty() }?.map { it.second.consumed.kcal }?.average(),
            logged.takeIf { it.isNotEmpty() }?.map { it.second.expenditure.projectedDayKcal }?.average(),
            logged.size, current ?: m.s.profile.currentWeightLb,
            points.minOfOrNull { it.date } ?: today, today,
        )
        if (rec != null && !dismissedRec) {
            SectionHeader("Recommendation")
            FormaCard {
                Text(rec.headline, style = Forma.type.heading, color = c.text)
                Spacer(Modifier.height(Space.xs))
                Text(rec.explanation, style = Forma.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.m))
                Row {
                    StatBlock("Observed", "${Fmt.signedOne(rec.observedLbPerWeek)}", "lb/wk", Modifier.weight(1f))
                    StatBlock("Expected from logs", rec.expectedLbPerWeek?.let { Fmt.signedOne(-it) } ?: "—", "lb/wk", Modifier.weight(1f))
                    StatBlock("Implied maintenance", rec.observedMaintenanceKcal?.let { Fmt.kcal(it) } ?: "—", null, Modifier.weight(1f))
                }
                if (rec.deficitChangeKcal != 0) {
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        PrimaryButton(if (rec.deficitChangeKcal > 0) "Increase deficit by ${rec.deficitChangeKcal}" else "Reduce deficit by ${-rec.deficitChangeKcal}",
                            { vm.applyDeficitChange(rec.deficitChangeKcal); dismissedRec = true }, Modifier.weight(1f))
                    }
                    TextAction("Not now", { dismissedRec = true }, color = c.textSecondary)
                }
                Text("Based on your 7-day trend over the last 4 weeks. Nothing changes unless you apply it.", style = Forma.type.caption, color = c.textTertiary)
            }
        } else if (rec == null) {
            Spacer(Modifier.height(Space.m))
            Note("After about 14 days of weigh-ins, FORMA can compare your trend with your logged intake and suggest a small adjustment.")
        }

        // ── Calorie balance ─────────────────────────────────────
        SectionHeader("Calorie balance · 14 days")
        val last14 = m.history(14)
        val bars = last14.map { (d, sm) ->
            d to if (!m.hasFood(d)) null else if (d == today) sm.actualDeficitSoFar else sm.projectedDeficit
        }
        val completeDays = last14.filter { m.hasFood(it.first) && it.first != today }
        val last7 = completeDays.filter { !it.first.isBefore(today.minusDays(7)) }
        FormaCard {
            Row {
                StatBlock("7-day avg deficit", last7.takeIf { it.isNotEmpty() }?.map { it.second.projectedDeficit }?.average()?.roundToInt()?.let { Fmt.signed(it).removePrefix("+") } ?: "—", "kcal", Modifier.weight(1f))
                StatBlock("7-day avg intake", last7.takeIf { it.isNotEmpty() }?.map { it.second.consumed.kcal }?.average()?.let { Fmt.kcal(it) } ?: "—", "kcal", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.l))
            BalanceBars(bars, average = last7.takeIf { it.isNotEmpty() }?.map { it.second.projectedDeficit }?.average()?.roundToInt())
            Spacer(Modifier.height(Space.s))
            Text("Up = estimated deficit, down = surplus. Days without food logged are skipped. Completed days only in averages.",
                style = Forma.type.caption, color = c.textTertiary)
            if (completeDays.isNotEmpty()) {
                Spacer(Modifier.height(Space.m)); Hairline()
                completeDays.takeLast(7).reversed().forEach { (d, sm) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(Fmt.medDate(d), style = Forma.type.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text("${Fmt.kcal(sm.consumed.kcal)} in", style = Forma.type.numberSmall, color = c.textTertiary, modifier = Modifier.weight(1f))
                        Text("${Fmt.kcal(sm.expenditure.projectedDayKcal)} out", style = Forma.type.numberSmall, color = c.textTertiary, modifier = Modifier.weight(1f))
                        Text(Fmt.signed(sm.projectedDeficit).removePrefix("+"), style = Forma.type.numberSmall,
                            color = if (sm.projectedDeficit >= 0) c.accent else c.negative)
                    }
                }
            }
        }

        // ── Activity & health trends ───────────────────────────
        SectionHeader("Activity & health trends")
        FormaCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            val shown = com.forma.app.ui.metrics.Metric.entries.filter { mt ->
                mt == com.forma.app.ui.metrics.Metric.WORKOUTS ||
                    com.forma.app.ui.metrics.MetricValues.series(mt, m, 30, s.settings).any { it.second != null }
            }
            shown.forEachIndexed { i, mt ->
                if (i > 0) Hairline()
                val last7 = com.forma.app.ui.metrics.MetricValues.series(mt, m, 7, s.settings).mapNotNull { it.second }
                val spark = com.forma.app.ui.metrics.MetricValues.series(mt, m, 30, s.settings).mapNotNull { it.second }
                val weeklyWorkouts = if (mt == com.forma.app.ui.metrics.Metric.WORKOUTS) com.forma.app.ui.metrics.MetricValues.weekly(mt, m, 8, s.settings).mapNotNull { it.second } else emptyList()
                Row(Modifier.fillMaxWidth().clickable { nav.navigate(com.forma.app.ui.nav.Routes.metric(mt.name)) }.padding(vertical = Space.m)
                    .testTag("trend_${mt.name}"), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(mt.label, style = Forma.type.body, color = c.text)
                        Text(
                            if (mt == com.forma.app.ui.metrics.Metric.WORKOUTS) "${weeklyWorkouts.lastOrNull()?.toInt() ?: 0} this week · ${Fmt.one(weeklyWorkouts.average().takeIf { !it.isNaN() } ?: 0.0)}/week avg"
                            else last7.takeIf { it.isNotEmpty() }?.let { "7-day avg ${mt.format(it.average())} ${mt.unit(s.settings)}" } ?: "No data in the last 7 days",
                            style = Forma.type.caption, color = c.textTertiary,
                        )
                    }
                    com.forma.app.ui.components.Sparkline(if (mt == com.forma.app.ui.metrics.Metric.WORKOUTS) weeklyWorkouts else spark,
                        Modifier.size(width = 72.dp, height = 28.dp))
                }
            }
        }
        Text("Weekly averages matter more than any single day. Tap a row for 7, 30 and 90-day views.", style = Forma.type.caption, color = c.textTertiary,
            modifier = Modifier.padding(top = Space.xs))

        // ── Waist ───────────────────────────────────────────────
        SectionHeader("Waist", action = "Add", onAction = { waistSheet = true })
        FormaCard {
            val lu = s.settings.lengthUnit
            if (s.measurements.isEmpty()) {
                EmptyState("No measurements", "Measure at the navel, relaxed, at the same time of day.") {
                    SecondaryButton("Add measurement", { waistSheet = true }, Modifier.testTag("add_waist"))
                }
            } else {
                val first = s.measurements.first(); val last = s.measurements.last()
                Row {
                    StatBlock("Latest", Fmt.length(last.waistIn, lu), Fmt.lengthUnit(lu), Modifier.weight(1f), style = Forma.type.numberLarge)
                    StatBlock("Change", Fmt.signedOne(Fmt.fromIn(last.waistIn - first.waistIn, lu)), Fmt.lengthUnit(lu), Modifier.weight(1f), style = Forma.type.numberLarge,
                        valueColor = if (last.waistIn < first.waistIn) c.accent else c.text)
                }
                if (s.measurements.size >= 2) {
                    Spacer(Modifier.height(Space.m))
                    TrendChart(emptyList(), s.measurements.map { ChartPoint(LocalDate.ofEpochDay(it.epochDay), Fmt.fromIn(it.waistIn, lu)) }, height = 120.dp)
                }
                Spacer(Modifier.height(Space.s))
                s.measurements.reversed().take(6).forEach { me ->
                    Hairline()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                            Text("${Fmt.length(me.waistIn, lu)} ${Fmt.lengthUnit(lu)}", style = Forma.type.number, color = c.text)
                            Text(Fmt.fullDate(LocalDate.ofEpochDay(me.epochDay)) + (me.note?.let { " · $it" } ?: ""), style = Forma.type.caption, color = c.textTertiary)
                        }
                        IconAction(Icons.Outlined.DeleteOutline, "Delete measurement", { vm.deleteWaist(me.id) }, tint = c.textTertiary)
                    }
                }
            }
        }

        // ── Workouts ────────────────────────────────────────────
        SectionHeader("Workout completion · 6 weeks")
        FormaCard {
            val weeks = (5 downTo 0).map { TrendAnalyzer.weekStart(today).minusWeeks(it.toLong()) }
            val cat = catalog
            val counts = weeks.map { ws ->
                (0..6).map { ws.plusDays(it.toLong()) }.count { d ->
                    s.workouts.any { it.completedAt != null && it.epochDay == d.toEpochDay() } && (cat?.planFor(d)?.required ?: true)
                }
            }
            val req = cat?.requiredInWeekOf(today) ?: 5
            WeekBars(counts, req)
            Spacer(Modifier.height(Space.s))
            Row {
                weeks.forEach { Text(Fmt.shortDate(it), style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Spacer(Modifier.height(Space.m))
            Text("This week ${counts.last()} of $req · ${s.workouts.count { it.completedAt != null }} sessions total", style = Forma.type.bodySmall, color = c.textSecondary)
        }

        // ── Protein ─────────────────────────────────────────────
        SectionHeader("Protein consistency · 14 days")
        FormaCard {
            val target = m.s.profile.proteinTargetG
            val days = m.history(14).map { (d, sm) -> d to (if (m.hasFood(d)) sm.consumed.proteinG else null) }
            val hit = days.count { (_, p) -> p != null && p >= target * 0.9 }
            val loggedDays = days.count { it.second != null }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { (_, p) ->
                    val f = if (p == null) null else (p / target).toFloat()
                    Canvas(Modifier.size(width = 14.dp, height = 44.dp)) {
                        drawRoundRect(c.track, Offset.Zero, size, CornerRadius(6f))
                        if (f != null) {
                            val h = size.height * f.coerceIn(0f, 1f)
                            drawRoundRect(if (f >= 0.9f) c.accent else c.textTertiary, Offset(0f, size.height - h), Size(size.width, h), CornerRadius(6f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            Text("Hit ≥90% of $target g on $hit of $loggedDays logged days", style = Forma.type.bodySmall, color = c.textSecondary)
        }

        // ── Weigh-in history ─────────────────────────────────────
        if (m.s.weights.isNotEmpty()) {
            SectionHeader("Weigh-ins")
            FormaCard {
                m.s.weights.sortedByDescending { it.measuredAt }.take(10).forEachIndexed { i, w ->
                    if (i > 0) Hairline()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                            Text("${Fmt.weight(w.weightLb, unit)} $ul", style = Forma.type.number, color = c.text)
                            Text(Fmt.fullDate(LocalDate.ofEpochDay(w.epochDay)) + if (w.source == "HEALTH_CONNECT") " · Health Connect" else "",
                                style = Forma.type.caption, color = c.textTertiary)
                        }
                        if (w.source == "MANUAL") IconAction(Icons.Outlined.DeleteOutline, "Delete weigh-in", { deleteWeight = w }, tint = c.textTertiary)
                    }
                }
            }
        }
    }

    if (weightSheet) WeightSheet(unit, m.s.profile.currentWeightLb, { weightSheet = false }) { vm.logWeight(it); weightSheet = false }
    if (waistSheet) WaistSheet(s.settings, today, { waistSheet = false }) { v, d, n -> vm.addWaist(v, d, n); waistSheet = false }
    deleteWeight?.let { w ->
        ConfirmDialog("Delete weigh-in?", "${Fmt.weight(w.weightLb, unit)} $ul on ${Fmt.fullDate(LocalDate.ofEpochDay(w.epochDay))}", "Delete",
            { vm.deleteWeight(w.id); deleteWeight = null }, { deleteWeight = null }, destructive = true)
    }
}

@Composable
private fun WeekBars(counts: List<Int>, required: Int) {
    val c = Forma.colors
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val slot = size.width / counts.size
        val bw = slot * 0.36f
        counts.forEachIndexed { i, n ->
            val x = slot * i + (slot - bw) / 2
            drawRoundRect(c.track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 2))
            val h = size.height * (n.toFloat() / required).coerceIn(0f, 1f)
            if (h > 0) drawRoundRect(if (n >= required) c.accent else c.accent.copy(alpha = 0.55f), Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}

@Composable
private fun WaistSheet(settings: AppSettings, today: LocalDate, onDismiss: () -> Unit, onSave: (Double, LocalDate, String?) -> Unit) {
    val lu = settings.lengthUnit
    var v by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(today) }
    FormaSheet(onDismiss, "Waist measurement", "Measure around the navel after a normal exhale, tape level and snug.") {
        FormaField(v, { v = it }, "Waist", suffix = Fmt.lengthUnit(lu), numeric = true, modifier = Modifier.testTag("waist_input"))
        Spacer(Modifier.height(Space.s))
        FormaField(note, { note = it }, "Note (optional)")
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Chip("Today", day == today, { day = today })
            Chip("Yesterday", day == today.minusDays(1), { day = today.minusDays(1) })
        }
        Spacer(Modifier.height(Space.l))
        val d = v.toDoubleOrNull()
        PrimaryButton("Save", { d?.let { onSave(Fmt.toIn(it, lu), day, note.ifBlank { null }) } }, Modifier.fillMaxWidth().testTag("waist_save"), enabled = d != null)
    }
}

