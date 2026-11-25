package com.forma.app.ui.report

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.forma.app.data.report.ReportNotifier
import com.forma.app.data.report.WeeklyReport
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.EmptyState
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.IconAction
import com.forma.app.ui.components.ListRow
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.Overline
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.ThinProgress
import com.forma.app.ui.components.Tone
import com.forma.app.ui.components.ValueRow
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.SleepScore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

object ReportRoutes {
    const val LIST = "reports"
    fun report(week: Long) = "report/$week"
}

internal fun weekTitle(r: WeeklyReport) =
    if (r.start.month == r.end.month) "${Fmt.shortDate(r.start)} – ${r.end.dayOfMonth}" else "${Fmt.shortDate(r.start)} – ${Fmt.shortDate(r.end)}"

internal fun hm(min: Int) = "${min / 60}h ${"%02d".format(min % 60)}m"

/** "+120" / "−80" with a color: green when the change is good. */
@Composable
private fun Delta(diff: Double?, goodWhenUp: Boolean, fmt: (Double) -> String, modifier: Modifier = Modifier) {
    if (diff == null || abs(diff) < 1e-9) return
    val c = Forma.colors
    val good = (diff > 0) == goodWhenUp
    Text((if (diff > 0) "▲ " else "▼ ") + fmt(abs(diff)) + " vs last week", style = Forma.type.caption,
        color = if (good) c.accent else c.warn, modifier = modifier)
}

/** Home card: from Sunday 6 am until dismissed. */
@Composable
fun WeeklyReportCard(nav: NavController) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val reports by remember { container.reports.all }.collectAsStateWithLifecycle(emptyList())
    val ready = container.reports.latestReadyWeek().toEpochDay()
    val e = reports.firstOrNull { it.weekStartEpochDay == ready && !it.dismissed } ?: return
    val r = remember(e) { container.reports.decode(e) }
    val c = Forma.colors
    Spacer(Modifier.height(Space.l))
    FormaCard(Modifier.testTag("weekly_card"), onClick = { nav.navigate(ReportRoutes.report(r.weekStart)) },
        padding = androidx.compose.foundation.layout.PaddingValues(start = Space.l, end = Space.xs, top = Space.xs, bottom = Space.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = Space.m)) {
                Overline("Your week · ${weekTitle(r)}")
                if (r.prs.isNotEmpty()) Text("🏆 ${r.prs.size} new PR${if (r.prs.size == 1) "" else "s"}", style = Forma.type.bodyStrong, color = c.accent)
            }
            IconAction(Icons.Outlined.Close, "Dismiss weekly report", { scope.launch { container.reports.dismiss(r.start) } },
                tint = c.textTertiary, modifier = Modifier.testTag("weekly_dismiss"))
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.padding(end = Space.m)) {
            StatBlock("Avg eaten", r.kcalAvg?.let(Fmt::kcal) ?: "—", "kcal", Modifier.weight(1f))
            StatBlock("Deficit", Fmt.kcal(r.deficitTotal), "kcal", Modifier.weight(1f), valueColor = if (r.deficitTotal >= 0) c.accent else c.warn)
        }
        Spacer(Modifier.height(Space.m))
        Row(Modifier.padding(end = Space.m)) {
            StatBlock("Sleep score", r.sleepScore?.toString() ?: "—", r.sleepScore?.let { SleepScore.label(it) }, Modifier.weight(1f))
            StatBlock("Steps / day", r.stepsAvg?.let { Fmt.int(it) } ?: "—", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.m))
        Text("${r.workoutsDone} of ${r.workoutsPlanned} workouts · tap for the full report",
            style = Forma.type.caption, color = c.textSecondary, modifier = Modifier.padding(end = Space.m))
    }
}

/** Ask once for notification permission (Android 13+) where it matters. */
@Composable
fun NotifyPrompt(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ReportNotifier.canNotify(ctx)) }
    val enabled by remember { container.settings.weeklyReportNotify }.collectAsStateWithLifecycle(true)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        scope.launch { container.settings.setWeeklyReportNotify(ok) }
    }
    if (granted && enabled) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Get this as a notification every Sunday at 6 am.", style = Forma.type.caption, color = Forma.colors.textSecondary, modifier = Modifier.weight(1f))
        TextAction("Turn on", {
            if (!granted && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else scope.launch { container.settings.setWeeklyReportNotify(true) }
        }, modifier = Modifier.testTag("notify_on"))
    }
}

@Composable
fun WeeklyReportScreen(nav: NavController, weekStart: Long) {
    val container = LocalContainer.current
    val reports by remember { container.reports.all }.collectAsStateWithLifecycle(null)
    val list = reports ?: return Box(Modifier.fillMaxSize())
    val e = list.firstOrNull { it.weekStartEpochDay == weekStart }
    val c = Forma.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("weekly_report")) {
        BackBar("Weekly report", { nav.popBackStack() }) {
            TextAction("All weeks", { nav.navigate(ReportRoutes.LIST) { launchSingleTop = true } }, color = c.textSecondary)
        }
        if (e == null) { EmptyState("No report for this week", "Reports are made for weeks with logged food, activity, sleep or workouts."); return }
        val r = remember(e) { container.reports.decode(e) }
        val prev = remember(list, weekStart) { list.firstOrNull { it.weekStartEpochDay == weekStart - 7 }?.let(container.reports::decode) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Text("Week of ${Fmt.medDate(r.start)} – ${Fmt.medDate(r.end)}", style = Forma.type.title, color = c.text, modifier = Modifier.testTag("report_title"))
            Spacer(Modifier.height(Space.s))
            NotifyPrompt()

            // ── Personal records ─────────────────────────────
            SectionHeader("Personal records")
            FormaCard(Modifier.testTag("report_prs")) {
                when {
                    !r.prsUnlocked -> {
                        val unlock = r.prsUnlockDay?.let(LocalDate::ofEpochDay)
                        Text(if (unlock == null) "Log a workout to start recording your bests." else
                            "Recording your baseline. PRs start being called out from ${Fmt.medDate(unlock)}, after 4 weeks of workouts.",
                            style = Forma.type.bodySmall, color = c.textSecondary)
                        unlock?.let {
                            val total = 28f; val left = (it.toEpochDay() - r.end.toEpochDay()).coerceIn(0, 28)
                            Spacer(Modifier.height(Space.s)); ThinProgress(1f - left / total)
                        }
                    }
                    r.prs.isEmpty() -> Text("No new PRs this week. Matching your best counts as consistency — the next jump comes from small increases.",
                        style = Forma.type.bodySmall, color = c.textSecondary)
                    else -> {
                        Text("You broke ${r.prs.size} PR${if (r.prs.size == 1) "" else "s"} this week", style = Forma.type.heading, color = c.accent)
                        r.prs.forEachIndexed { i, p ->
                            if (i > 0) Hairline()
                            ValueRow(p.exercise, p.now, "was ${p.before}", valueColor = c.accent)
                        }
                        Text("A PR is a higher estimated strength: more weight, more reps, or both.", style = Forma.type.caption, color = c.textTertiary)
                    }
                }
            }

            // ── Food & deficit ───────────────────────────────
            SectionHeader("Calories")
            FormaCard {
                Row {
                    StatBlock("Avg per day", r.kcalAvg?.let(Fmt::kcal) ?: "—", "kcal", Modifier.weight(1f))
                    StatBlock("Total eaten", Fmt.kcal(r.kcalTotal), "kcal", Modifier.weight(1f))
                }
                Delta(if (r.kcalAvg != null && prev?.kcalAvg != null) (r.kcalAvg - prev.kcalAvg).toDouble() else null, goodWhenUp = false, { Fmt.kcal(it) + " kcal/day" })
                Spacer(Modifier.height(Space.s))
                Text("${r.daysLogged} of 7 days logged" + (r.budgetAvg?.let { " · avg budget ${Fmt.kcal(it)} kcal" } ?: ""),
                    style = Forma.type.caption, color = c.textTertiary)
            }
            Spacer(Modifier.height(Space.m))
            FormaCard(Modifier.testTag("report_deficit")) {
                Row {
                    StatBlock("Weekly deficit", Fmt.kcal(r.deficitTotal), "kcal", Modifier.weight(1f), valueColor = if (r.deficitTotal >= 0) c.accent else c.warn)
                    StatBlock("Per day", r.deficitAvg?.let(Fmt::kcal) ?: "—", "kcal", Modifier.weight(1f))
                }
                Delta(prev?.takeIf { it.daysLogged > 0 && r.daysLogged > 0 }?.let { (r.deficitTotal - it.deficitTotal).toDouble() }, goodWhenUp = true, { Fmt.kcal(it) + " kcal" })
                Spacer(Modifier.height(Space.s))
                Text("Estimated burn minus food, on days with food logged. Your weight trend shows what it adds up to.",
                    style = Forma.type.caption, color = c.textTertiary)
                r.weightAvg?.let { w ->
                    Hairline(Modifier.padding(vertical = Space.s))
                    ValueRow("Average weight", "${Fmt.one(w)} lb",
                        r.weightChange?.let { (if (it <= 0) "▼ " else "▲ ") + "${Fmt.one(abs(it))} lb vs last week" } ?: "Weigh in next week to see the change",
                        valueColor = c.text)
                }
            }

            // ── Sleep ────────────────────────────────────────
            SectionHeader("Sleep")
            FormaCard(Modifier.testTag("report_sleep")) {
                if (r.sleepNights > 0 && r.sleepScore == null) {
                    StatBlock("Avg asleep", r.sleepAvgMin?.let(::hm) ?: "—", null)
                    Text("${r.sleepNights} night${if (r.sleepNights == 1) "" else "s"} recorded without sleep stages, so there's no score — only hours.",
                        style = Forma.type.caption, color = c.textSecondary)
                } else if (r.sleepNights == 0) Text("No sleep recorded this week. Wear your watch to bed and allow Sleep in Health Connect.",
                    style = Forma.type.bodySmall, color = c.textSecondary)
                else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        StatBlock("Sleep score", "${r.sleepScore}", "/ 100", Modifier.weight(1f), style = Forma.type.numberLarge,
                            valueColor = scoreColor(r.sleepScore!!))
                        StatBlock("Avg asleep", r.sleepAvgMin?.let(::hm) ?: "—", null, Modifier.weight(1f))
                    }
                    Text(SleepScore.label(r.sleepScore!!) + " · ${r.sleepNights} night${if (r.sleepNights == 1) "" else "s"} recorded",
                        style = Forma.type.caption, color = c.textSecondary)
                    Delta(prev?.sleepScore?.let { (r.sleepScore!! - it).toDouble() }, goodWhenUp = true, { "${it.roundToInt()} pts" })
                    if (r.sleepFromStages) {
                        Spacer(Modifier.height(Space.m))
                        StageBar(r)
                    }
                    Hairline(Modifier.padding(vertical = Space.s))
                    r.bestNightDay?.let { ValueRow("Best night", "${r.bestNightScore}", Fmt.dow(LocalDate.ofEpochDay(it)) + " morning", valueColor = c.accent) }
                    r.worstNightDay?.let { ValueRow("Toughest night", "${r.worstNightScore}", Fmt.dow(LocalDate.ofEpochDay(it)) + " morning", valueColor = c.warn) }
                    Text(
                        "FORMA's score: duration up to 50 (full at 7 h+, the adult recommendation), deep ≥ 13 % and REM ≥ 20 % of sleep up to 25, " +
                            "sleep efficiency ≥ 85 % up to 25. Nights without sleep stages aren't scored.",
                        style = Forma.type.caption, color = c.textTertiary,
                    )
                }
            }

            // ── Steps ────────────────────────────────────────
            SectionHeader("Steps")
            FormaCard(Modifier.testTag("report_steps")) {
                if (r.stepDays == 0) Text("No steps synced this week.", style = Forma.type.bodySmall, color = c.textSecondary)
                else {
                    Row {
                        StatBlock("Total", Fmt.int(r.stepsTotal), null, Modifier.weight(1f))
                        StatBlock("Per day", r.stepsAvg?.let { Fmt.int(it) } ?: "—", null, Modifier.weight(1f))
                    }
                    Delta(if (r.stepsAvg != null && prev?.stepsAvg != null) (r.stepsAvg - prev.stepsAvg).toDouble() else null, goodWhenUp = true, { Fmt.int(it) + " steps/day" })
                    Spacer(Modifier.height(Space.s))
                    Text("Goal of ${Fmt.int(r.stepGoal)} hit on ${r.stepGoalDays} of ${r.stepDays} days" +
                        (r.stepsBestDay?.let { " · best ${Fmt.dow(LocalDate.ofEpochDay(it))} (${Fmt.int(r.stepsBest ?: 0)})" } ?: ""),
                        style = Forma.type.caption, color = c.textTertiary)
                }
            }

            // ── Training ─────────────────────────────────────
            SectionHeader("Training")
            FormaCard(Modifier.testTag("report_training")) {
                Row {
                    StatBlock("Workouts", "${r.workoutsDone}", "of ${r.workoutsPlanned}", Modifier.weight(1f))
                    StatBlock("Time training", if (r.trainingMinutes > 0) hm(r.trainingMinutes) else "—", null, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.s))
                ThinProgress(if (r.workoutsPlanned == 0) 0f else (r.workoutsDone / r.workoutsPlanned.toFloat()).coerceAtMost(1f))
            }
            Spacer(Modifier.height(Space.l))
            Text("Saved on ${Fmt.medDate(container.time.dayOf(r.generatedAt))}. Reports are kept; nothing is deleted.",
                style = Forma.type.caption, color = c.textTertiary)
        }
    }
}

@Composable
private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Forma.colors.accent
    score >= 60 -> Forma.colors.text
    else -> Forma.colors.warn
}

/** Average night split into deep / REM / light / awake. */
@Composable
private fun StageBar(r: WeeklyReport) {
    val c = Forma.colors
    val parts = listOf("Deep" to r.deepAvgMin, "REM" to r.remAvgMin, "Light" to r.lightAvgMin, "Awake" to r.awakeAvgMin)
    val total = parts.sumOf { it.second ?: 0 }.coerceAtLeast(1)
    Column(Modifier.testTag("stage_bar")) {
        parts.forEach { (label, min) ->
            if (min == null) return@forEach
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = Forma.type.caption, color = c.textSecondary, modifier = Modifier.width(52.dp))
                ThinProgress(min / total.toFloat(), Modifier.weight(1f), color = when (label) {
                    "Deep" -> c.accent; "REM" -> c.accent.copy(alpha = 0.65f); "Light" -> c.textTertiary; else -> c.warn
                }, height = 8.dp)
                Text("${hm(min)}  ${(100 * min / total)}%", style = Forma.type.caption, color = c.textTertiary,
                    modifier = Modifier.width(96.dp).padding(start = Space.s))
            }
        }
    }
}

@Composable
fun WeeklyReportsScreen(nav: NavController) {
    val container = LocalContainer.current
    val reports by remember { container.reports.all.map { l -> l.map(container.reports::decode) } }.collectAsStateWithLifecycle(null)
    val c = Forma.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("weekly_reports")) {
        BackBar("Weekly reports", { nav.popBackStack() })
        val list = reports ?: return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            NotifyPrompt()
            if (list.isEmpty()) EmptyState("No reports yet", "Your first report arrives Sunday at 6 am, covering Sunday to Saturday.")
            else FormaCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l)) {
                list.forEachIndexed { i, r ->
                    if (i > 0) Hairline()
                    ListRow(weekTitle(r),
                        listOfNotNull(
                            r.kcalAvg?.let { "${Fmt.kcal(it)} kcal/day" },
                            "deficit ${Fmt.kcal(r.deficitTotal)}",
                            r.sleepScore?.let { "sleep $it" },
                            "${r.workoutsDone}/${r.workoutsPlanned} workouts",
                        ).joinToString(" · "),
                        onClick = { nav.navigate(ReportRoutes.report(r.weekStart)) },
                        trailing = { if (r.prs.isNotEmpty()) Tag("${r.prs.size} PR${if (r.prs.size == 1) "" else "s"}", Tone.ACCENT) },
                    )
                }
            }
        }
    }
}
