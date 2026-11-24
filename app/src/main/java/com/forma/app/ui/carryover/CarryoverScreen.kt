package com.forma.app.ui.carryover

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.db.CarryoverEntity
import com.forma.app.data.repo.EnergyModel
import com.forma.app.data.seed.Category
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.ConfirmDialog
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.SwitchRow
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.Tone
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.nav.Routes
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.CarryoverKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class CarryoverViewModel(private val c: AppContainer) : ViewModel() {
    val state = combine(c.energyModel.filterNotNull(), c.carryover.entries) { m, e -> m to e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun addOverage(kcal: Int, day: LocalDate, note: String?) = viewModelScope.launch {
        c.carryover.add(kcal, CarryoverKind.OVERAGE.name, day.toEpochDay(), note)
    }

    /** Unlogged food: counted in that day's intake as an estimate, and optionally repaid through carryover. */
    fun addUnlogged(kcal: Int, day: LocalDate, note: String?, carry: Boolean) = viewModelScope.launch {
        val at = if (day == c.time.today()) c.time.nowMillis() else c.time.startOfDayMillis(day) + 20 * 3_600_000L
        c.food.logManual("Unlogged food (estimate)", Category.SNACKS.name, kcal.toDouble(), null, null, null, day.toEpochDay(), at,
            note = note ?: "Added from Calorie Carryover")
        if (carry) c.carryover.add(kcal, CarryoverKind.UNLOGGED_ADJUSTMENT.name, day.toEpochDay(), note)
    }

    fun update(e: CarryoverEntity) = viewModelScope.launch { c.carryover.update(e) }
    fun setCancelled(e: CarryoverEntity, v: Boolean) = viewModelScope.launch { c.carryover.cancel(e, v) }
    fun delete(e: CarryoverEntity) = viewModelScope.launch { c.carryover.delete(e.id) }
}

@Composable
fun CarryoverScreen(nav: NavController) {
    val vm = formaViewModel { CarryoverViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val (m, entries) = st ?: return Box(Modifier.fillMaxSize())
    val c = Forma.colors
    val co = m.carryover
    val today = m.s.today
    val todaySummary = m.today
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CarryoverEntity?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("carryover")) {
        BackBar("Calorie carryover", { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            FormaCard {
                Row {
                    StatBlock("Outstanding", Fmt.kcal(co.balanceAfterToday), "kcal", Modifier.weight(1f), style = Forma.type.numberLarge)
                    StatBlock("Repaying today", Fmt.kcal(todaySummary.budget.repaymentApplied), "kcal", Modifier.weight(1f),
                        style = Forma.type.numberLarge, valueColor = if (todaySummary.budget.repaymentApplied > 0) c.warn else c.text)
                }
                Spacer(Modifier.height(Space.m))
                Text(
                    when {
                        co.balanceAfterToday > 0 && co.projectedClearDate != null ->
                            "Projected to clear ${Fmt.relativeDay(co.projectedClearDate!!, today).let { if (it == "Today" || it == "Tomorrow") it.lowercase() else "on $it" }}, " +
                                "repaying up to ${m.s.profile.maxRepaymentPerDay} kcal a day."
                        co.balanceStartOfToday > 0 -> "Clears today with this repayment."
                        co.clearedOn != null -> "Balance cleared on ${Fmt.medDate(co.clearedOn!!)}."
                        else -> "No outstanding balance."
                    },
                    style = Forma.type.bodySmall, color = c.textSecondary, modifier = Modifier.testTag("carryover_status"),
                )
                if (todaySummary.budget.repaymentApplied < todaySummary.budget.repaymentScheduled) {
                    Spacer(Modifier.height(Space.s))
                    Note("Today's repayment is limited to ${todaySummary.budget.repaymentApplied} kcal so your budget stays at or above ${Fmt.kcal(m.s.profile.minimumBudgetKcal)}.", Tone.WARN)
                }
            }
            Spacer(Modifier.height(Space.m))
            PrimaryButton("Record an overage", { adding = true }, Modifier.fillMaxWidth().testTag("record_overage"), icon = Icons.Outlined.Add)
            Spacer(Modifier.height(Space.m))
            Note(
                "Carryover only adjusts upcoming budgets. It never adds calories to your food log, and it's kept separate from your " +
                    "actual deficit. If you already logged everything you ate, that food is already counted in your intake — recording " +
                    "an overage simply spreads a small budget reduction over the next days. Don't log the same food again.",
            )

            if (co.history.any { it.repaid > 0 || it.scheduled > 0 } || co.projection.isNotEmpty()) {
                SectionHeader("Schedule")
                FormaCard {
                    val rows = co.history.filter { !it.date.isBefore(today.minusDays(6)) } + co.projection.take(10)
                    rows.forEachIndexed { i, d ->
                        if (i > 0) Hairline()
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                            Text(Fmt.relativeDay(d.date, today), style = Forma.type.body, color = if (d.projected) c.textSecondary else c.text, modifier = Modifier.weight(1f))
                            if (d.limitedByMinimum) { Tag("limited by minimum", Tone.WARN); Spacer(Modifier.padding(start = Space.s)) }
                            if (d.projected) { Tag("planned"); Spacer(Modifier.padding(start = Space.s)) }
                            Text("−${d.repaid}", style = Forma.type.number, color = if (d.projected) c.textSecondary else c.warn)
                        }
                    }
                    if (co.projection.isNotEmpty()) Text("Planned days assume your budget stays above the minimum.", style = Forma.type.caption, color = c.textTertiary)
                }
            }

            SectionHeader("Recorded")
            FormaCard {
                if (entries.isEmpty()) Text("Nothing recorded yet.", style = Forma.type.bodySmall, color = c.textSecondary)
                entries.forEachIndexed { i, e ->
                    if (i > 0) Hairline()
                    Row(Modifier.fillMaxWidth().clickable { editing = e }.padding(vertical = Space.m).testTag("co_${e.id}"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Fmt.kcal(e.kcal)} kcal", style = Forma.type.number, color = if (e.cancelled) c.textTertiary else c.text)
                            Text(
                                Fmt.medDate(LocalDate.ofEpochDay(e.epochDay)) + " · " +
                                    (if (e.kind == CarryoverKind.UNLOGGED_ADJUSTMENT.name) "Unlogged food" else "Over budget") +
                                    (e.note?.let { " · $it" } ?: ""),
                                style = Forma.type.caption, color = c.textTertiary,
                            )
                        }
                        if (e.cancelled) Tag("Cancelled")
                    }
                }
            }
            SectionHeader("Rules")
            FormaCard {
                Text("• Repayment starts the day after the overage.\n• At most ${m.s.profile.maxRepaymentPerDay} kcal is repaid per day.\n" +
                    "• Your budget never drops below ${Fmt.kcal(m.s.profile.minimumBudgetKcal)} kcal; anything that doesn't fit carries forward.\n" +
                    "• One big day is never turned into an aggressive restriction.",
                    style = Forma.type.bodySmall, color = c.textSecondary)
                com.forma.app.ui.components.TextAction("Change limits", { nav.navigate(Routes.TARGETS) })
            }
        }
    }

    if (adding) AddSheet(m, today, onDismiss = { adding = false },
        onOverage = { k, d, n -> vm.addOverage(k, d, n); adding = false },
        onUnlogged = { k, d, n, carry -> vm.addUnlogged(k, d, n, carry); adding = false })
    editing?.let { e -> EditSheet(e, { editing = null }, { vm.update(it); editing = null }, { vm.setCancelled(e, !e.cancelled); editing = null }, { vm.delete(e); editing = null }) }
}

private enum class Mode { OVER, UNLOGGED }

@Composable
private fun AddSheet(
    m: EnergyModel, today: LocalDate, onDismiss: () -> Unit,
    onOverage: (Int, LocalDate, String?) -> Unit, onUnlogged: (Int, LocalDate, String?, Boolean) -> Unit,
) {
    val c = Forma.colors
    var mode by remember { mutableStateOf(Mode.OVER) }
    var day by remember { mutableStateOf(today.minusDays(1)) }
    val s = m.summary(day)
    val detected = s.overBudgetKcal
    var amount by remember(day, mode) { mutableStateOf(if (mode == Mode.OVER && detected > 0) detected.toString() else "") }
    var note by remember { mutableStateOf("") }
    var carry by remember { mutableStateOf(true) }
    FormaSheet(onDismiss, "Record carryover") {
        Segmented(Mode.entries, mode, { if (it == Mode.OVER) "Went over budget" else "Ate more than I logged" }, { mode = it }, Modifier.fillMaxWidth().testTag("co_mode"))
        Spacer(Modifier.height(Space.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Chip("Yesterday", day == today.minusDays(1), { day = today.minusDays(1) })
            Chip("Today", day == today, { day = today })
        }
        Spacer(Modifier.height(Space.m))
        if (mode == Mode.OVER) {
            Text(
                if (m.hasFood(day)) "Logged ${Fmt.kcal(s.consumed.kcal)} kcal against a ${Fmt.kcal(s.budget.finalBudget)} kcal budget" +
                    (if (detected > 0) " — ${Fmt.kcal(detected)} over." else " — within budget.")
                else "No food logged for this day.",
                style = Forma.type.bodySmall, color = c.textSecondary,
            )
            Spacer(Modifier.height(Space.s))
            Note("This won't add calories to your log or change that day's deficit. It only lowers the next days' budgets by up to ${m.s.profile.maxRepaymentPerDay} kcal a day.")
        } else {
            Note("Use this for food you couldn't log precisely. The estimate is added to that day's intake (so the deficit stays honest) and, if you choose, repaid gently over the next days.")
        }
        Spacer(Modifier.height(Space.m))
        FormaField(amount, { amount = it }, "Calories", suffix = "kcal", numeric = true, decimal = false, modifier = Modifier.testTag("co_amount"))
        Spacer(Modifier.height(Space.s))
        FormaField(note, { note = it }, "Note (optional)")
        if (mode == Mode.UNLOGGED) SwitchRow("Also repay through carryover", "Lowers upcoming budgets to offset it.", carry, { carry = it })
        Spacer(Modifier.height(Space.l))
        val k = amount.toIntOrNull()
        PrimaryButton("Save", {
            if (k != null && k > 0) { if (mode == Mode.OVER) onOverage(k, day, note.ifBlank { null }) else onUnlogged(k, day, note.ifBlank { null }, carry) }
        }, Modifier.fillMaxWidth().testTag("co_save"), enabled = k != null && k in 1..5000)
    }
}

@Composable
private fun EditSheet(e: CarryoverEntity, onDismiss: () -> Unit, onSave: (CarryoverEntity) -> Unit, onToggleCancel: () -> Unit, onDelete: () -> Unit) {
    var amount by remember { mutableStateOf(e.kcal.toString()) }
    var note by remember { mutableStateOf(e.note ?: "") }
    var confirm by remember { mutableStateOf(false) }
    val c = Forma.colors
    FormaSheet(onDismiss, "Edit carryover", Fmt.fullDate(LocalDate.ofEpochDay(e.epochDay))) {
        FormaField(amount, { amount = it }, "Calories", suffix = "kcal", numeric = true, decimal = false, modifier = Modifier.testTag("co_edit_amount"))
        Spacer(Modifier.height(Space.s))
        FormaField(note, { note = it }, "Note")
        if (e.kind == CarryoverKind.UNLOGGED_ADJUSTMENT.name) {
            Spacer(Modifier.height(Space.s))
            Note("The matching \"Unlogged food\" entry stays in your food log; edit or delete it there if needed.")
        }
        Spacer(Modifier.height(Space.l))
        PrimaryButton("Save", { amount.toIntOrNull()?.takeIf { it in 1..5000 }?.let { onSave(e.copy(kcal = it, note = note.ifBlank { null })) } },
            Modifier.fillMaxWidth().testTag("co_edit_save"))
        Spacer(Modifier.height(Space.s))
        SecondaryButton(if (e.cancelled) "Restore" else "Cancel this overage", onToggleCancel, Modifier.fillMaxWidth().testTag("co_cancel"))
        Spacer(Modifier.height(Space.s))
        SecondaryButton("Delete", { confirm = true }, Modifier.fillMaxWidth(), color = c.negative)
    }
    if (confirm) ConfirmDialog("Delete this record?", "Repayments are recalculated without it.", "Delete", { confirm = false; onDelete() }, { confirm = false }, destructive = true)
}
