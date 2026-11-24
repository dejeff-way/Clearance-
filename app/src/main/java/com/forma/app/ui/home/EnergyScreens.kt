package com.forma.app.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.popOnMain
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.IconAction
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.SwitchRow
import com.forma.app.ui.components.Tone
import com.forma.app.ui.components.ValueRow
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.ExpenditureSource
import com.forma.engine.LineKind
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Shows exactly how the day's expenditure and budget were derived. */
@Composable
fun EnergyBreakdownScreen(nav: NavController, epochDay: Long) {
    val container = LocalContainer.current
    val model by container.energyModel.collectAsStateWithLifecycle()
    val sessions by container.activity.sessions.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val m = model ?: return Box(Modifier.fillMaxSize())
    val c = Forma.colors
    val day = LocalDate.ofEpochDay(epochDay)
    val s = m.summary(day)
    val e = s.expenditure
    val b = s.budget
    val p = m.profileFor(day)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar(Fmt.relativeDay(day, m.s.today) + " · the math", { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Note(
                when (e.source) {
                    ExpenditureSource.HEALTH_CONNECT_TOTAL -> "Expenditure uses Health Connect's total calories burned. Nothing that's already inside that number is added again."
                    ExpenditureSource.HEALTH_CONNECT_ACTIVE -> "Expenditure = estimated resting energy (BMR) + Health Connect active calories."
                    ExpenditureSource.ESTIMATED -> "No measured expenditure for this day. FORMA is estimating from your profile and logged activity."
                },
                if (e.source == ExpenditureSource.ESTIMATED) Tone.NEUTRAL else Tone.ACCENT,
            )

            SectionHeader("At a glance")
            FormaCard {
                ValueRow("1 · Baseline energy", Fmt.kcal(e.baselineKcal), if (e.source == ExpenditureSource.ESTIMATED) "BMR × activity factor (estimated)" else "Resting energy for the day")
                ValueRow("2 · Activity", Fmt.kcal(e.activeKcal), when (e.source) {
                    ExpenditureSource.ESTIMATED -> "Estimated from steps or logged exercise"
                    else -> "From your wearable via Health Connect" + if (e.wearableAdjustmentKcal != 0.0) " (adjusted ${Fmt.signed(e.wearableAdjustmentKcal.toInt())} kcal)" else ""
                })
                ValueRow("3 · Total energy expenditure (est.)", Fmt.kcal(e.projectedDayKcal), if (e.dayComplete) "Whole day" else "Projected for the whole day", valueColor = c.accent)
                ValueRow("4 · Food eaten", Fmt.kcal(s.consumed.kcal))
                ValueRow("5 · Estimated deficit", Fmt.signed(s.projectedDeficit).removePrefix("+"), "Expenditure (3) − food (4)" +
                    if (!e.dayComplete) " if you eat nothing more today" else "", valueColor = if (s.projectedDeficit >= 0) c.accent else c.negative)
                Text("Wearable calorie figures are estimates, not exact measurements.", style = Forma.type.caption, color = c.textTertiary)
            }

            SectionHeader("1 · Estimated expenditure")
            FormaCard {
                e.lines.forEach { l ->
                    ValueRow(
                        l.label,
                        l.kcal?.let { Fmt.kcal(it) } ?: "not added",
                        l.detail, muted = l.kind == LineKind.EXCLUDED, strike = l.kind == LineKind.EXCLUDED && l.kcal != null,
                    )
                }
                Hairline()
                if (!e.dayComplete) ValueRow("Burned so far", Fmt.kcal(e.burnedSoFarKcal))
                ValueRow(if (e.dayComplete) "Total for the day" else "Projected for the full day", Fmt.kcal(e.projectedDayKcal), valueColor = c.accent)
                e.notes.forEach { n -> Text(n, style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = Space.xs)) }
            }

            SectionHeader("2 · Budget")
            FormaCard {
                if (p.budgetMode == com.forma.engine.BudgetMode.DYNAMIC) {
                    ValueRow("Projected expenditure", Fmt.kcal(e.projectedDayKcal))
                    ValueRow("Planned deficit", "−${Fmt.kcal(p.desiredDeficitKcal)}")
                } else {
                    ValueRow("Fixed calorie target", Fmt.kcal(p.fixedBudgetKcal ?: b.rawBudget), "Set in Settings → Targets")
                }
                if (b.cappedByActivityLimit) ValueRow("Activity limit", "≤ ${Fmt.kcal((b.sedentaryBudget ?: 0) + (p.maxActivityBudgetIncreaseKcal ?: 0))}",
                    "Activity can raise the budget by at most ${p.maxActivityBudgetIncreaseKcal} kcal (Settings → Targets)", valueColor = c.warn)
                if (b.heldAtMinimum) ValueRow("Held at minimum", Fmt.kcal(p.minimumBudgetKcal), "Raised from ${Fmt.kcal(b.rawBudget)}", valueColor = c.warn)
                if (b.repaymentScheduled > 0) {
                    ValueRow("Carryover repayment", "−${b.repaymentApplied}",
                        if (b.repaymentApplied < b.repaymentScheduled) "Scheduled ${b.repaymentScheduled}, limited by your minimum budget" else "Max ${p.maxCarryoverRepaymentPerDay}/day", valueColor = c.warn)
                }
                Hairline()
                ValueRow("Calorie budget", Fmt.kcal(b.finalBudget), valueColor = c.accent)
            }
            b.warnings.forEach { Spacer(Modifier.height(Space.s)); Note(it, Tone.WARN) }

            SectionHeader("3 · Intake and deficit")
            FormaCard {
                ValueRow("Consumed (logged)", Fmt.kcal(s.consumed.kcal))
                ValueRow("Remaining budget", Fmt.kcal(s.remainingKcal), "Budget − consumed. This is not a deficit.",
                    valueColor = if (s.remainingKcal < 0) c.negative else c.text)
                Hairline()
                ValueRow(if (e.dayComplete) "Actual estimated deficit" else "Estimated deficit so far",
                    Fmt.signed(s.actualDeficitSoFar).removePrefix("+"),
                    "Burned ${if (e.dayComplete) "" else "so far "}− consumed. Negative means a surplus.",
                    valueColor = if (s.actualDeficitSoFar >= 0) c.accent else c.negative)
                if (!e.dayComplete) ValueRow("If you eat nothing more", Fmt.signed(s.projectedDeficit).removePrefix("+"), "Projected expenditure − consumed")
                ValueRow("Planned deficit", Fmt.kcal(b.plannedDeficit), "What your budget is built around")
            }

            val daySessions = sessions.filter { it.epochDay == epochDay }
            if (daySessions.isNotEmpty()) {
                SectionHeader("Exercise sessions")
                FormaCard {
                    daySessions.forEach { ss ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                                Text(ss.title, style = Forma.type.body, color = c.text)
                                Text("${ss.durationMinutes.toInt()} min · " + (if (ss.source == "HEALTH_CONNECT") "Health Connect" else if (ss.recordedByDevice) "Manual · also on device" else "Manual · not on device") +
                                    (ss.kcal?.let { " · ${it.toInt()} kcal" } ?: ""), style = Forma.type.caption, color = c.textTertiary)
                            }
                            if (ss.source == "MANUAL") IconAction(Icons.Outlined.DeleteOutline, "Delete session", { scope.launch { container.activity.delete(ss.id) } }, tint = c.textTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            Text("BMR ${Fmt.kcal(e.bmrKcal)} kcal (Mifflin–St Jeor, ${Fmt.one(p.weightLb)} lb). All values are estimates.",
                style = Forma.type.caption, color = c.textTertiary)
        }
    }
}

private data class Preset(val label: String, val met: Double)

private val presets = listOf(
    Preset("Walk", 3.8), Preset("Incline walk", 5.0), Preset("Cycling", 6.5), Preset("Strength", 4.5),
    Preset("Elliptical", 5.5), Preset("Swim", 7.0), Preset("Skiing", 6.5), Preset("Other", 4.0),
)

/** Manual exercise entry with the double-counting switch explained. */
@Composable
fun ActivityAddScreen(nav: NavController, epochDay: Long) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val sync by container.syncMeta.collectAsStateWithLifecycle(null)
    val hcConnected = !sync?.grantedPermissions.isNullOrBlank()
    val c = Forma.colors
    var preset by remember { mutableStateOf(presets[0]) }
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var onDevice by remember { mutableStateOf(hcConnected) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar("Add exercise", { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Text("Type", style = Forma.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Space.s))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                presets.forEach { pr -> Chip(pr.label, pr == preset, { preset = pr }) }
            }
            Spacer(Modifier.height(Space.l))
            FormaField(title, { title = it }, "Name (optional)", placeholder = preset.label)
            Spacer(Modifier.height(Space.m))
            FormaField(minutes, { minutes = it }, "Duration", suffix = "min", numeric = true, decimal = false, modifier = Modifier.testTag("exercise_minutes"))
            Spacer(Modifier.height(Space.m))
            FormaField(kcal, { kcal = it }, "Calories (optional)", suffix = "kcal", numeric = true, decimal = false,
                supporting = "From a machine or watch. Leave empty and FORMA estimates from the activity type.")
            Spacer(Modifier.height(Space.s))
            SwitchRow(
                "Also recorded by my watch or phone",
                "If on, this session is already inside your Health Connect totals and won't be added again.",
                onDevice, { onDevice = it },
            )
            Note("FORMA never adds the same workout twice. Sessions recorded by a device are only counted when no measured total exists for the day.")
            error?.let { Spacer(Modifier.height(Space.s)); Note(it, Tone.NEGATIVE) }
            Spacer(Modifier.height(Space.l))
            PrimaryButton("Save exercise", {
                val min = minutes.toDoubleOrNull()
                if (min == null || min <= 0) { error = "Enter a duration"; return@PrimaryButton }
                scope.launch {
                    runCatching {
                        container.activity.addManual(
                            epochDay, title.ifBlank { preset.label }, min, kcal.toDoubleOrNull(), preset.met, onDevice,
                            if (epochDay == container.time.today().toEpochDay()) container.time.nowMillis() - (min * 60_000).toLong() else null, null,
                        )
                    }.onSuccess { nav.popOnMain() }.onFailure { error = it.message }
                }
            }, Modifier.fillMaxWidth().testTag("exercise_save"))
        }
    }
}
