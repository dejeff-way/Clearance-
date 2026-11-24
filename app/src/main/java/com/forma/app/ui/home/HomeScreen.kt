package com.forma.app.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.forma.app.data.seed.Category
import com.forma.app.ui.components.CalorieRing
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.Overline
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Sparkline
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.ThinProgress
import com.forma.app.ui.components.Tone
import com.forma.app.ui.components.WeightSheet
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.nav.Routes
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.ExpenditureSource
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun HomeScreen(nav: NavController) {
    val vm = formaViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var weightSheet by remember { mutableStateOf(false) }
    val s = state ?: return Box(Modifier.fillMaxSize())
    val c = Forma.colors
    val sum = s.summary
    val day = s.today.toEpochDay()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = Space.gutter).padding(bottom = Space.xxl).testTag("home"),
    ) {
        // ── Header ───────────────────────────────────────────────
        Spacer(Modifier.height(Space.l))
        Overline(Fmt.longDate(s.today))
        Spacer(Modifier.height(Space.xs))
        Text(greeting(s.profile.displayName), style = Forma.type.display, color = c.text)
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Tag("${Fmt.weight(s.profile.currentWeightLb, s.settings.weightUnit)} ${Fmt.weightUnit(s.settings.weightUnit)}")
            Tag(activityStatus(s), tone = if (s.workout.done) Tone.ACCENT else Tone.NEUTRAL)
        }

        // ── Daily summary hero ───────────────────────────────────
        Spacer(Modifier.height(Space.xl))
        FormaCard(onClick = { nav.navigate(Routes.energy(day)) }, padding = androidx.compose.foundation.layout.PaddingValues(Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Daily summary", Modifier.weight(1f))
                SourceTag(sum.expenditure.source, sum.expenditure.healthConnectTotalRejected)
            }
            Spacer(Modifier.height(Space.l))
            CalorieRing(sum.consumed.kcal, sum.budget.finalBudget, Modifier.align(Alignment.CenterHorizontally)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val over = sum.remainingKcal < 0
                    Text(if (over) "Over budget" else "Remaining", style = Forma.type.caption, color = if (over) c.negative else c.textSecondary)
                    Text(Fmt.kcal(kotlin.math.abs(sum.remainingKcal)), style = Forma.type.numberHero,
                        color = if (over) c.negative else c.text, modifier = Modifier.testTag("remaining_kcal"))
                    Text("of ${Fmt.kcal(sum.budget.finalBudget)} kcal", style = Forma.type.caption, color = c.textTertiary,
                        modifier = Modifier.testTag("budget_kcal"))
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(Modifier.fillMaxWidth()) {
                StatBlock("Eaten", Fmt.kcal(sum.consumed.kcal), "kcal", Modifier.weight(1f))
                StatBlock("Burned · est.", Fmt.kcal(sum.expenditure.burnedSoFarKcal), "kcal", Modifier.weight(1f))
                StatBlock("Deficit so far", Fmt.signed(sum.actualDeficitSoFar).removePrefix("+"), "kcal", Modifier.weight(1f),
                    valueColor = if (sum.actualDeficitSoFar >= 0) c.accent else c.negative)
            }
            Spacer(Modifier.height(Space.l))
            Hairline()
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Projected expenditure ${Fmt.kcal(sum.expenditure.projectedDayKcal)} · planned deficit ${Fmt.kcal(sum.budget.plannedDeficit)}",
                        style = Forma.type.caption, color = c.textSecondary)
                    if (sum.budget.repaymentApplied > 0) {
                        Text("Carryover repayment −${sum.budget.repaymentApplied} kcal today", style = Forma.type.caption, color = c.warn)
                    }
                }
                Icon(Icons.Outlined.ChevronRight, "See calculation", tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
        sum.budget.warnings.forEach { w ->
            Spacer(Modifier.height(Space.s)); Note(w, Tone.WARN)
        }

        // ── Quick actions ────────────────────────────────────────
        Spacer(Modifier.height(Space.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            PrimaryButton("Add food", { nav.navigate(Routes.addFood(day, suggestedMeal().name)) },
                Modifier.weight(1f).testTag("add_food"), icon = Icons.Outlined.Add)
            SecondaryButton("Log weight", { weightSheet = true }, Modifier.weight(1f).testTag("log_weight"), icon = Icons.Outlined.MonitorWeight)
        }

        // ── Protein ──────────────────────────────────────────────
        Spacer(Modifier.height(Space.m))
        FormaCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Overline("Protein")
                    Spacer(Modifier.height(Space.xs))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${sum.consumed.proteinG.roundToInt()}", style = Forma.type.numberLarge, color = c.text)
                        Text(" / ${sum.proteinTargetG} g", style = Forma.type.bodySmall, color = c.textTertiary, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                Text(if (sum.proteinRemainingG > 0) "${sum.proteinRemainingG} g to go" else "Target reached",
                    style = Forma.type.label, color = if (sum.proteinRemainingG > 0) c.textSecondary else c.accent)
            }
            Spacer(Modifier.height(Space.m))
            ThinProgress((sum.consumed.proteinG / sum.proteinTargetG).toFloat())
        }

        // ── Activity ─────────────────────────────────────────────
        Spacer(Modifier.height(Space.m))
        val energy = s.model.s.energy[day]
        val hcConnected = !s.sync?.grantedPermissions.isNullOrBlank()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            MiniCard(Icons.AutoMirrored.Outlined.DirectionsWalk, "Steps",
                energy?.steps?.let { Fmt.int(it) } ?: "—",
                if (energy?.steps != null) "Health Connect" else if (hcConnected) "No data yet" else "Not connected",
                Modifier.weight(1f)) { nav.navigate(Routes.HEALTH) }
            MiniCard(Icons.Outlined.Bedtime, "Sleep",
                energy?.sleepMinutes?.let { "${(it / 60).toInt()}h ${(it % 60).toInt()}m" } ?: "—",
                if (energy?.sleepMinutes != null) "Last night" else if (hcConnected) "No data" else "Not connected",
                Modifier.weight(1f)) { nav.navigate(Routes.HEALTH) }
        }

        // ── Today's meals ────────────────────────────────────────
        SectionHeader("Today's meals", action = "Food log", onAction = { nav.navigate(Routes.log(day)) })
        FormaCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.l, vertical = Space.s)) {
            if (s.meals.isEmpty()) {
                Text("Nothing logged yet. Pick from your recipes or enter calories manually.",
                    style = Forma.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(vertical = Space.m))
            } else {
                Category.entries.forEachIndexed { i, meal ->
                    val items = s.meals.filter { it.mealType == meal.name }
                    if (items.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(meal.label, style = Forma.type.caption, color = c.textTertiary)
                                items.forEach { Text(it.name, style = Forma.type.body, color = c.text) }
                            }
                            Text(Fmt.kcal(items.sumOf { it.kcal }), style = Forma.type.number, color = c.text)
                        }
                    }
                }
            }
            Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
                com.forma.app.ui.components.TextAction("Add food", { nav.navigate(Routes.addFood(day, suggestedMeal().name)) })
                com.forma.app.ui.components.TextAction("Add exercise", { nav.navigate(Routes.activity(day)) }, color = c.textSecondary)
            }
        }

        // ── Today's workout ─────────────────────────────────────
        SectionHeader("Today's workout")
        val w = s.workout
        FormaCard(onClick = { nav.navigate(Routes.WORKOUT) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (w.done) c.accentSoft else c.surfaceAlt), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.FitnessCenter, null, tint = if (w.done) c.accent else c.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(w.day.title, style = Forma.type.heading, color = c.text)
                    Text(
                        when {
                            w.day.rest -> "No workout scheduled — rest well."
                            w.done -> "Completed · ${w.totalSlots} exercises"
                            w.session != null -> "${w.completedSlots} of ${w.totalSlots} done"
                            w.day.optional -> "Optional · ${w.day.focus}"
                            else -> "${w.day.estimatedMinutes} min · ${w.day.focus}"
                        },
                        style = Forma.type.bodySmall, color = c.textSecondary,
                    )
                    s.otherSessionToday?.let { o ->
                        Text("Also logged: ${o.title}${if (o.completedAt != null) " ✓" else ""}", style = Forma.type.caption, color = c.textTertiary)
                    }
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = c.textTertiary)
            }
            if (!w.day.rest && w.totalSlots > 0) {
                Spacer(Modifier.height(Space.m))
                ThinProgress(w.completedSlots / w.totalSlots.toFloat(), height = 3.dp)
            }
        }

        // ── Weight trend ────────────────────────────────────────
        SectionHeader("Weight trend", action = "Progress", onAction = { nav.navigate(Routes.PROGRESS) })
        FormaCard(onClick = { nav.navigate(Routes.PROGRESS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("7-day average", style = Forma.type.caption, color = c.textSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(s.trendWeight?.let { Fmt.weight(it, s.settings.weightUnit) } ?: "—", style = Forma.type.numberLarge, color = c.text)
                        Text(" ${Fmt.weightUnit(s.settings.weightUnit)}", style = Forma.type.bodySmall, color = c.textTertiary, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text(
                        when {
                            !s.hasWeighIns -> "Log your first weigh-in to start the trend."
                            s.weekChange == null -> "Trend builds after a week of weigh-ins."
                            else -> "${Fmt.signedOne(Fmt.fromLb(s.weekChange, s.settings.weightUnit))} ${Fmt.weightUnit(s.settings.weightUnit)} vs. last week"
                        },
                        style = Forma.type.caption, color = c.textTertiary,
                    )
                }
                Sparkline(s.weightTrend, Modifier.width(110.dp).height(44.dp))
            }
        }

        // ── Carryover ───────────────────────────────────────────
        SectionHeader("Calorie carryover", action = "Manage", onAction = { nav.navigate(Routes.CARRYOVER) })
        val co = s.model.carryover
        FormaCard(onClick = { nav.navigate(Routes.CARRYOVER) }) {
            if (co.balanceStartOfToday == 0 && co.balanceAfterToday == 0) {
                Text("No outstanding balance", style = Forma.type.bodyStrong, color = c.text)
                Text("If you go over budget with food you didn't log, record it here and it will be repaid gently over the next days.",
                    style = Forma.type.caption, color = c.textTertiary)
            } else {
                Row {
                    StatBlock("Outstanding", Fmt.kcal(co.balanceAfterToday), "kcal", Modifier.weight(1f))
                    StatBlock("Repaying today", Fmt.kcal(sum.budget.repaymentApplied), "kcal", Modifier.weight(1f), valueColor = c.warn)
                    StatBlock("Clears", co.projectedClearDate?.let { Fmt.relativeDay(it, s.today) } ?: "—", null, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        Text("Calorie and expenditure figures are estimates, not exact physiological measurements.",
            style = Forma.type.caption, color = c.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }

    if (weightSheet) {
        WeightSheet(s.settings.weightUnit, s.profile.currentWeightLb, { weightSheet = false }) { lb ->
            vm.logWeight(lb) {}
            weightSheet = false
        }
    }
}

@Composable
private fun SourceTag(src: ExpenditureSource, rejected: Boolean) {
    when (src) {
        ExpenditureSource.HEALTH_CONNECT_TOTAL -> Tag("Health Connect", Tone.ACCENT)
        ExpenditureSource.HEALTH_CONNECT_ACTIVE -> Tag("HC active + BMR", Tone.ACCENT)
        ExpenditureSource.ESTIMATED -> Tag(if (rejected) "Estimated · HC incomplete" else "Estimated", if (rejected) Tone.WARN else Tone.NEUTRAL)
    }
}

@Composable
private fun MiniCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Forma.colors
    FormaCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Space.xs))
            Text(label, style = Forma.type.caption, color = c.textSecondary)
        }
        Spacer(Modifier.height(Space.s))
        Text(value, style = Forma.type.numberLarge, color = c.text)
        Text(sub, style = Forma.type.caption, color = c.textTertiary)
    }
}

private fun greeting(name: String): String {
    val h = LocalTime.now().hour
    val g = when {
        h < 5 -> "Good evening"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) g else "$g, $name"
}

fun suggestedMeal(): Category {
    val h = LocalTime.now().hour
    return when {
        h in 4..10 -> Category.BREAKFAST
        h in 11..15 -> Category.LUNCH
        h in 16..20 -> Category.SUPPER
        else -> Category.SNACKS
    }
}

private fun activityStatus(s: HomeState): String {
    val w = s.workout
    return when {
        w.done -> "Workout done"
        w.day.rest -> "Rest day · Shabbos"
        w.day.optional -> "Optional session"
        w.session != null -> "Workout in progress"
        else -> "Training day"
    }
}
