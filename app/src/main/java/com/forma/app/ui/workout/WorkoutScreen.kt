package com.forma.app.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.db.ExerciseLogEntity
import com.forma.app.data.db.SetLogEntity
import com.forma.app.data.db.WorkoutSessionEntity
import com.forma.app.data.seed.Exercise
import com.forma.app.data.seed.ExerciseKind
import com.forma.app.data.seed.ExerciseLibrary
import com.forma.app.data.seed.Slot
import com.forma.app.data.seed.WorkoutDay
import com.forma.app.data.seed.WorkoutProgram
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.CheckCircle
import com.forma.app.ui.components.ConfirmDialog
import com.forma.app.ui.components.DayMark
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.ScreenTitle
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.SwitchRow
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.ThinProgress
import com.forma.app.ui.components.Tone
import com.forma.app.ui.components.drawDayMark
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Radius
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.Progression
import com.forma.engine.ProgressionAdvice
import com.forma.engine.SetResult
import com.forma.engine.TrendAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SlotState(
    val slot: Slot,
    val exercise: Exercise,
    val substituted: Boolean,
    val remembered: Boolean,
    val log: ExerciseLogEntity?,
    val sets: List<SetLogEntity>,
    val lastSets: List<SetLogEntity>,
    val lastDate: LocalDate?,
    val advice: ProgressionAdvice?,
)

data class WeekDayState(val date: LocalDate, val plan: WorkoutDay, val mark: DayMark)

data class WorkoutState(
    val today: LocalDate,
    val selected: WorkoutDay,
    val session: WorkoutSessionEntity?,
    val slots: List<SlotState>,
    val week: List<WeekDayState>,
    val completedThisWeek: Int,
    val history: List<WorkoutSessionEntity>,
)

class WorkoutViewModel(private val c: AppContainer) : ViewModel() {
    val selectedKey = MutableStateFlow(WorkoutProgram.forDay(c.time.today().dayOfWeek).key)

    val state = combine(
        selectedKey, c.workouts.sessions, c.workouts.exerciseLogs, c.workouts.setLogs, c.workouts.prefs,
    ) { key, sessions, logs, sets, prefs ->
        val today = c.time.today()
        val plan = WorkoutProgram.byKey(key)!!
        val session = sessions.firstOrNull { it.epochDay == today.toEpochDay() && it.templateKey == key }
        val prefMap = prefs.associate { it.slotKey to it.exerciseKey }
        val sessionById = sessions.associateBy { it.id }
        val slots = plan.slots.map { slot ->
            val log = session?.let { s -> logs.firstOrNull { it.sessionId == s.id && it.slotKey == slot.key } }
            val exKey = log?.exerciseKey ?: prefMap[slot.key] ?: slot.exerciseKey
            // Most recent earlier performance of this same exercise (any day/slot).
            val prev = logs.filter { it.exerciseKey == exKey && it.sessionId != session?.id }
                .mapNotNull { l -> sessionById[l.sessionId]?.let { s -> l to s } }
                .filter { (l, _) -> sets.any { it.exerciseLogId == l.id && (it.reps != null || it.durationMin != null) } }
                .maxByOrNull { (_, s) -> s.epochDay }
            val lastSets = prev?.let { (l, _) -> sets.filter { it.exerciseLogId == l.id }.sortedBy { it.setIndex } }.orEmpty()
            val ex = ExerciseLibrary.get(exKey)
            SlotState(
                slot = slot, exercise = ex, substituted = exKey != slot.exerciseKey,
                remembered = prefMap[slot.key] != null,
                log = log, sets = log?.let { l -> sets.filter { it.exerciseLogId == l.id }.sortedBy { it.setIndex } }.orEmpty(),
                lastSets = lastSets, lastDate = prev?.second?.epochDay?.let(LocalDate::ofEpochDay),
                advice = if (slot.repRange != null && ex.tracksWeight && lastSets.isNotEmpty())
                    Progression.advise(slot.repRange, lastSets.map { SetResult(it.weightLb, it.reps) }, ex.incrementLb) else null,
            )
        }
        val weekStart = TrendAnalyzer.weekStart(today)
        val week = (0..6).map { i ->
            val d = weekStart.plusDays(i.toLong())
            val p = WorkoutProgram.forDay(d.dayOfWeek)
            val done = sessions.any { it.epochDay == d.toEpochDay() && it.completedAt != null }
            val mark = when {
                p.rest -> if (done) DayMark.DONE else DayMark.REST
                p.optional -> if (done) DayMark.OPTIONAL_DONE else DayMark.OPTIONAL
                done -> DayMark.DONE
                d.isBefore(today) -> DayMark.MISSED
                else -> DayMark.PLANNED
            }
            WeekDayState(d, p, mark)
        }
        val completedRequired = week.count { it.mark == DayMark.DONE && !it.plan.optional && !it.plan.rest }
        WorkoutState(today, plan, session, slots, week, completedRequired,
            sessions.filter { it.completedAt != null }.sortedByDescending { it.completedAt }.take(8))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun select(key: String) { selectedKey.value = key }

    private suspend fun sessionId(): Long = c.workouts.ensureSession(c.time.today(), selectedKey.value)

    fun start() = viewModelScope.launch { sessionId() }
    fun toggle(slot: String, done: Boolean) = viewModelScope.launch { c.workouts.setCompleted(sessionId(), slot, done) }
    fun substitute(slot: String, ex: String, remember: Boolean) = viewModelScope.launch { c.workouts.substitute(sessionId(), slot, ex, remember) }
    fun forget(slot: String) = viewModelScope.launch { c.workouts.clearRememberedSubstitution(slot) }
    fun logSet(slot: String, idx: Int, w: Double?, reps: Int?, min: Double?) = viewModelScope.launch { c.workouts.logSet(sessionId(), slot, idx, w, reps, min) }
    fun notes(slot: String, n: String) = viewModelScope.launch { c.workouts.setExerciseNotes(sessionId(), slot, n) }
    fun sessionNotes(n: String) = viewModelScope.launch { c.workouts.setSessionNotes(sessionId(), n) }
    fun finish() = viewModelScope.launch { c.workouts.finish(sessionId()) }
    fun reopen() = viewModelScope.launch { c.workouts.reopen(sessionId()) }
    fun discard(id: Long) = viewModelScope.launch { c.workouts.deleteSession(id) }
}

@Composable
fun WorkoutScreen(nav: NavController) {
    val vm = formaViewModel { WorkoutViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return Box(Modifier.fillMaxSize())
    val c = Forma.colors
    var swapFor by remember { mutableStateOf<SlotState?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val plan = s.selected
    val scheduledToday = WorkoutProgram.forDay(s.today.dayOfWeek).key == plan.key

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = Space.gutter).padding(bottom = Space.xxl).testTag("workout"),
    ) {
        ScreenTitle("Workout", subtitle = "${s.completedThisWeek} of ${WorkoutProgram.requiredPerWeek} sessions this week")
        Spacer(Modifier.height(Space.m))
        WeekStrip(s.week, s.today, plan.key) { vm.select(it) }
        Spacer(Modifier.height(Space.s))
        ThinProgress(s.completedThisWeek / WorkoutProgram.requiredPerWeek.toFloat(), height = 3.dp)

        Spacer(Modifier.height(Space.l))
        FormaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.day.name.lowercase().replaceFirstChar { it.uppercase() }, style = Forma.type.overline.copy(), color = c.textTertiary, modifier = Modifier.weight(1f))
                when {
                    plan.rest -> Tag("Rest")
                    plan.optional -> Tag("Optional")
                    s.session?.completedAt != null -> Tag("Completed", Tone.ACCENT)
                    else -> Tag("${plan.estimatedMinutes} min")
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text(plan.title, style = Forma.type.titleSmall, color = c.text, modifier = Modifier.testTag("workout_title"))
            Text(plan.focus, style = Forma.type.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(Space.m))
            Text(plan.notes, style = Forma.type.bodySmall, color = c.textSecondary)
            if (!scheduledToday && !plan.rest) {
                Spacer(Modifier.height(Space.m))
                Note("This is ${plan.day.name.lowercase().replaceFirstChar { it.uppercase() }}'s plan. Starting it logs the session for today.")
            }
        }

        if (plan.rest) {
            Spacer(Modifier.height(Space.l))
            Note("Friday is a rest day for Shabbos. No workout is scheduled — recovery is part of the plan.")
        } else {
            if (s.session == null) {
                Spacer(Modifier.height(Space.l))
                PrimaryButton(if (plan.optional) "Start optional session" else "Start workout", { vm.start() }, Modifier.fillMaxWidth().testTag("start_workout"))
            }
            SectionHeader("Exercises")
            s.slots.forEachIndexed { i, ss ->
                ExerciseCard(i + 1, ss, active = s.session != null,
                    onToggle = { vm.toggle(ss.slot.key, !(ss.log?.completed ?: false)) },
                    onSwap = { swapFor = ss },
                    onSet = { idx, w, r, m -> vm.logSet(ss.slot.key, idx, w, r, m) },
                    onNotes = { vm.notes(ss.slot.key, it) })
                Spacer(Modifier.height(Space.m))
            }
            if (s.session != null) {
                var notes by remember(s.session.id) { mutableStateOf(s.session.notes ?: "") }
                FormaField(notes, { notes = it; vm.sessionNotes(it) }, "Session notes", singleLine = false)
                Spacer(Modifier.height(Space.m))
                if (s.session.completedAt == null) {
                    PrimaryButton("Finish workout", { vm.finish() }, Modifier.fillMaxWidth().testTag("finish_workout"))
                } else {
                    Note("Workout complete. Nice work — this counts toward your weekly total.", Tone.ACCENT)
                    Spacer(Modifier.height(Space.s))
                    SecondaryButton("Reopen workout", { vm.reopen() }, Modifier.fillMaxWidth())
                }
                TextAction("Discard this session", { confirmDiscard = true }, color = c.textTertiary, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }

        if (s.history.isNotEmpty()) {
            SectionHeader("Recent sessions")
            FormaCard {
                s.history.forEachIndexed { i, h ->
                    if (i > 0) Hairline()
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                        Text(h.title, style = Forma.type.body, color = c.text, modifier = Modifier.weight(1f))
                        Text(Fmt.relativeDay(LocalDate.ofEpochDay(h.epochDay), s.today), style = Forma.type.caption, color = c.textTertiary)
                    }
                }
            }
        }
    }

    swapFor?.let { ss ->
        SubstitutionSheet(ss, onDismiss = { swapFor = null }, onPick = { ex, remember ->
            vm.substitute(ss.slot.key, ex, remember); swapFor = null
        }, onForget = { vm.forget(ss.slot.key); swapFor = null })
    }
    if (confirmDiscard && s.session != null) {
        ConfirmDialog("Discard session?", "All checkmarks, sets and notes for today's ${plan.title} will be deleted.", "Discard",
            onConfirm = { vm.discard(s.session.id); confirmDiscard = false }, onDismiss = { confirmDiscard = false }, destructive = true)
    }
}

@Composable
private fun WeekStrip(week: List<WeekDayState>, today: LocalDate, selected: String, onSelect: (String) -> Unit) {
    val c = Forma.colors
    Row(Modifier.fillMaxWidth()) {
        week.forEach { d ->
            val sel = d.plan.key == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.m))
                    .then(if (sel) Modifier.background(c.surface).border(1.dp, c.line, RoundedCornerShape(Radius.m)) else Modifier)
                    .clickable { onSelect(d.plan.key) }.padding(vertical = Space.s).testTag("day_${d.plan.key}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Fmt.dowLetter(d.date.dayOfWeek), style = Forma.type.caption, color = if (d.date == today) c.accent else c.textTertiary)
                Spacer(Modifier.height(2.dp))
                Text("${d.date.dayOfMonth}", style = Forma.type.numberSmall, color = if (sel) c.text else c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.size(10.dp)) { drawDayMark(d.mark, Offset(size.width / 2, size.height / 2), size.width / 2.4f, c) }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    n: Int, ss: SlotState, active: Boolean, onToggle: () -> Unit, onSwap: () -> Unit,
    onSet: (Int, Double?, Int?, Double?) -> Unit, onNotes: (String) -> Unit,
) {
    val c = Forma.colors
    var expanded by rememberSaveable(ss.slot.key) { mutableStateOf(false) }
    val done = ss.log?.completed == true
    FormaCard(padding = androidx.compose.foundation.layout.PaddingValues(start = Space.xs, end = Space.l, top = Space.s, bottom = Space.m)) {
        Row(verticalAlignment = Alignment.Top) {
            if (active) CheckCircle(done, onToggle, "Mark ${ss.exercise.name} complete", Modifier.testTag("check_${ss.slot.key}"))
            else Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Text("$n", style = Forma.type.numberSmall, color = c.textTertiary) }
            Column(Modifier.weight(1f).padding(top = 10.dp).clickable { expanded = !expanded }) {
                Text(ss.exercise.name, style = Forma.type.heading, color = if (done) c.textSecondary else c.text, modifier = Modifier.testTag("exname_${ss.slot.key}"))
                Text(
                    "${if (ss.slot.sets > 1) "${ss.slot.sets} × " else ""}${ss.slot.target}" + (if (ss.slot.restSeconds > 0) " · rest ${restLabel(ss.slot.restSeconds)}" else ""),
                    style = Forma.type.bodySmall, color = c.textSecondary,
                )
                if (ss.substituted) Text("Substitute for ${ExerciseLibrary.get(ss.slot.exerciseKey).name}${if (ss.remembered) " · remembered" else ""}",
                    style = Forma.type.caption, color = c.warn)
            }
            TextAction("Swap", onSwap, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp).testTag("swap_${ss.slot.key}"))
        }
        Column(Modifier.padding(start = 44.dp)) {
            Text(ss.exercise.instruction, style = Forma.type.caption, color = c.textTertiary, maxLines = if (expanded) 10 else 2)
            ss.advice?.let { a ->
                Spacer(Modifier.height(Space.s))
                Text(a.message, style = Forma.type.caption, color = c.accent)
            }
            if (ss.lastSets.isNotEmpty()) {
                Text("Last (${ss.lastDate?.let { Fmt.shortDate(it) }}): " + ss.lastSets.joinToString("  ") { s ->
                    if (s.durationMin != null) "${Fmt.amount(s.durationMin)} min" else "${s.weightLb?.let { Fmt.amount(it) + "×" } ?: ""}${s.reps ?: "–"}"
                }, style = Forma.type.caption, color = c.textTertiary)
            }
            if (active) {
                Spacer(Modifier.height(Space.s))
                (0 until ss.slot.sets).forEach { idx ->
                    SetRow(ss, idx, onSet)
                }
                AnimatedVisibility(expanded) {
                    var notes by remember(ss.log?.id) { mutableStateOf(ss.log?.notes ?: "") }
                    Column {
                        Spacer(Modifier.height(Space.s))
                        FormaField(notes, { notes = it; onNotes(it) }, "Notes (seat height, grip, how it felt)", singleLine = false)
                    }
                }
                if (!expanded) TextAction(if (ss.log?.notes.isNullOrBlank()) "Add note" else "Note: ${ss.log?.notes}", { expanded = true }, color = c.textTertiary)
            }
        }
    }
}

@Composable
private fun SetRow(ss: SlotState, idx: Int, onSet: (Int, Double?, Int?, Double?) -> Unit) {
    val c = Forma.colors
    val existing = ss.sets.firstOrNull { it.setIndex == idx }
    val last = ss.lastSets.firstOrNull { it.setIndex == idx }
    val key = "${ss.log?.id}-${ss.exercise.key}-$idx"
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Set ${idx + 1}", style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.width(44.dp))
        if (ss.slot.timed) {
            var min by remember(key) { mutableStateOf(existing?.durationMin?.let { Fmt.amount(it) } ?: "") }
            FormaField(min, { min = it; onSet(idx, null, null, it.toDoubleOrNull()) }, "Minutes", Modifier.weight(1f).testTag("min_${ss.slot.key}_$idx"),
                numeric = true, placeholder = last?.durationMin?.let { Fmt.amount(it) })
        } else {
            var w by remember(key) { mutableStateOf(existing?.weightLb?.let { Fmt.amount(it) } ?: "") }
            var r by remember(key) { mutableStateOf(existing?.reps?.toString() ?: "") }
            if (ss.exercise.tracksWeight) {
                FormaField(w, { w = it; onSet(idx, it.toDoubleOrNull(), r.toIntOrNull(), null) }, "lb", Modifier.weight(1f).testTag("w_${ss.slot.key}_$idx"),
                    numeric = true, placeholder = last?.weightLb?.let { Fmt.amount(it) })
                Spacer(Modifier.width(Space.s))
            }
            FormaField(r, { r = it; onSet(idx, w.toDoubleOrNull(), it.toIntOrNull(), null) }, "reps", Modifier.weight(1f).testTag("r_${ss.slot.key}_$idx"),
                numeric = true, decimal = false, placeholder = last?.reps?.toString())
        }
    }
}

private fun restLabel(s: Int) = if (s >= 60 && s % 60 == 0) "${s / 60} min" else if (s >= 60) "${s / 60}:${"%02d".format(s % 60)}" else "$s s"

@Composable
private fun SubstitutionSheet(ss: SlotState, onDismiss: () -> Unit, onPick: (String, Boolean) -> Unit, onForget: () -> Unit) {
    val c = Forma.colors
    val original = ExerciseLibrary.get(ss.slot.exerciseKey)
    val options = (listOf(original) + ExerciseLibrary.substitutesFor(original.key)).distinctBy { it.key }
    var remember by remember { mutableStateOf(ss.remembered) }
    FormaSheet(onDismiss, "Swap ${original.name.lowercase()}", "Alternatives train the same muscles or movement pattern.") {
        options.forEach { ex ->
            val current = ex.key == ss.exercise.key
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).clickable { onPick(ex.key, remember) }
                    .padding(vertical = Space.m).testTag("sub_${ex.key}"),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.name, style = Forma.type.bodyStrong, color = c.text)
                        if (ex.key == original.key) { Spacer(Modifier.width(Space.s)); Tag("Original") }
                        if (current) { Spacer(Modifier.width(Space.s)); Tag("Current", Tone.ACCENT) }
                    }
                    Text(ex.instruction, style = Forma.type.caption, color = c.textTertiary)
                }
            }
            Hairline()
        }
        Spacer(Modifier.height(Space.s))
        SwitchRow("Use for future workouts", "Remember this choice for this slot in the program.", remember, { remember = it })
        if (ss.remembered) TextAction("Stop remembering a substitute", onForget, color = c.textSecondary)
    }
}

