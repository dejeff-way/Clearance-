package com.forma.app.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.db.ExerciseEntity
import com.forma.app.data.db.ExerciseLogEntity
import com.forma.app.data.db.SetLogEntity
import com.forma.app.data.db.WorkoutSessionEntity
import com.forma.app.data.db.WorkoutSlotEntity
import com.forma.app.data.seed.ExerciseKind
import com.forma.app.data.workout.PlannedDay
import com.forma.app.data.workout.ProgressionType
import com.forma.app.data.workout.WorkoutCatalog
import com.forma.app.data.workout.WorkoutDef
import com.forma.app.data.workout.SetTimer
import com.forma.app.data.workout.TimerAdvice
import com.forma.app.data.workout.TimerKind
import com.forma.app.data.workout.TimerTick
import androidx.compose.material.icons.outlined.Timer
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SlotState(
    val slot: WorkoutSlotEntity,
    val exercise: ExerciseEntity,
    val kind: ExerciseKind,
    val substituted: Boolean,
    val remembered: Boolean,
    val log: ExerciseLogEntity?,
    val sets: List<SetLogEntity>,
    val lastSets: List<SetLogEntity>,
    val lastDate: LocalDate?,
    val advice: ProgressionAdvice?,
    val substitutes: List<ExerciseEntity>,
    val originalName: String,
)

data class WeekDayState(val date: LocalDate, val plan: PlannedDay, val mark: DayMark)

data class WorkoutState(
    val today: LocalDate,
    val viewDate: LocalDate,
    val plan: PlannedDay,
    /** What's shown: today's override, else the scheduled workout. */
    val workout: WorkoutDef?,
    val overridden: Boolean,
    val session: WorkoutSessionEntity?,
    val slots: List<SlotState>,
    val week: List<WeekDayState>,
    val completedThisWeek: Int,
    val requiredThisWeek: Int,
    val programName: String,
    val programWeek: Int,
    val programWeeks: Int,
    val history: List<HistoryItem>,
    val catalog: WorkoutCatalog,
)

/** A history row: a FORMA workout, a watch workout, or both merged when they overlap. */
data class HistoryItem(
    val title: String,
    val date: LocalDate,
    val endedAt: Long,
    val forma: WorkoutSessionEntity?,
    val watch: com.forma.app.data.db.ExerciseSessionEntity?,
    val setsLogged: Int,
)

private data class Logs(val sessions: List<WorkoutSessionEntity>, val logs: List<ExerciseLogEntity>, val sets: List<SetLogEntity>, val prefs: Map<String, String>)

class WorkoutViewModel(private val c: AppContainer) : ViewModel() {
    val viewDate = MutableStateFlow(c.time.today())

    private val logs = combine(c.workouts.sessions, c.workouts.exerciseLogs, c.workouts.setLogs, c.workouts.prefs) { s, l, st, p ->
        Logs(s, l, st, p.associate { it.slotKey to it.exerciseKey })
    }

    val state = combine(viewDate, c.workoutOverride, c.catalog.filterNotNull(), logs, c.activity.sessions) { date, override, cat, lg, external ->
        val today = c.time.today()
        val plan = cat.planFor(date)
        val todayOverride = override?.takeIf { it.first == today }?.second?.let { cat.workoutsById[it] }
        val dateSessions = lg.sessions.filter { it.epochDay == date.toEpochDay() }
        // Show the session actually logged that day if there is one; otherwise the override (today) or the plan.
        val logged = dateSessions.firstOrNull { it.templateKey == (if (date == today) todayOverride?.id else null) }
            ?: dateSessions.firstOrNull { it.templateKey == plan.workout?.id } ?: dateSessions.firstOrNull()
        val workout = (if (date == today) todayOverride else null) ?: logged?.let { cat.workoutsById[it.templateKey] } ?: plan.workout
        val session = workout?.let { w -> dateSessions.firstOrNull { it.templateKey == w.id } }
        val sessionById = lg.sessions.associateBy { it.id }
        val slots = workout?.slots.orEmpty().map { slot ->
            val log = session?.let { s -> lg.logs.firstOrNull { it.sessionId == s.id && it.slotKey == slot.slotKey } }
            val exKey = log?.exerciseKey ?: lg.prefs[slot.slotKey] ?: slot.exerciseKey
            val ex = cat.exercise(exKey)
            val prev = lg.logs.filter { it.exerciseKey == exKey && it.sessionId != session?.id }
                .mapNotNull { l -> sessionById[l.sessionId]?.takeIf { it.epochDay <= date.toEpochDay() }?.let { s -> l to s } }
                .filter { (l, _) -> lg.sets.any { it.exerciseLogId == l.id && (it.reps != null || it.durationMin != null) } }
                .maxByOrNull { (_, s) -> s.epochDay }
            val lastSets = prev?.let { (l, _) -> lg.sets.filter { it.exerciseLogId == l.id }.sortedBy { it.setIndex } }.orEmpty()
            val range = if (slot.repMin != null && slot.repMax != null) slot.repMin..slot.repMax else null
            val inc = slot.incrementLb ?: ex.incrementLb
            val results = lastSets.map { SetResult(it.weightLb, it.reps) }
            val advice = if (range == null || !ex.tracksWeight || lastSets.isEmpty()) null else when (cat.progressionOf(slot)) {
                ProgressionType.DOUBLE -> Progression.advise(range, results, inc)
                ProgressionType.LINEAR -> Progression.adviseLinear(range, results, inc, slot.sets)
                ProgressionType.NONE -> null
            }
            SlotState(
                slot = slot, exercise = ex, kind = cat.kind(exKey), substituted = exKey != slot.exerciseKey, remembered = lg.prefs[slot.slotKey] != null,
                log = log, sets = log?.let { l -> lg.sets.filter { it.exerciseLogId == l.id }.sortedBy { it.setIndex } }.orEmpty(),
                lastSets = lastSets, lastDate = prev?.second?.epochDay?.let(LocalDate::ofEpochDay), advice = advice,
                substitutes = cat.substitutesFor(slot), originalName = cat.exercise(slot.exerciseKey).name,
            )
        }
        val weekStart = TrendAnalyzer.weekStart(today)
        val week = (0..6).map { i ->
            val d = weekStart.plusDays(i.toLong())
            val p = cat.planFor(d)
            val done = lg.sessions.any { it.epochDay == d.toEpochDay() && it.completedAt != null }
            val mark = when {
                done -> if (p.required) DayMark.DONE else DayMark.OPTIONAL_DONE
                p.rest || p.workout == null -> DayMark.REST
                p.optional -> DayMark.OPTIONAL
                d.isBefore(today) -> DayMark.MISSED
                else -> DayMark.PLANNED
            }
            WeekDayState(d, p, mark)
        }
        val watch = external.filter { it.source == "HEALTH_CONNECT" }
        val formaDone = lg.sessions.filter { it.completedAt != null }
        val formaItems = formaDone.map { s ->
            val overlap = watch.firstOrNull { w -> w.epochDay == s.epochDay && com.forma.app.ui.metrics.MetricValues.overlaps(s.startedAt, s.completedAt, w.startTime, w.endTime) }
            HistoryItem(s.title, LocalDate.ofEpochDay(s.epochDay), s.completedAt!!, s, overlap,
                lg.logs.filter { it.sessionId == s.id }.sumOf { l -> lg.sets.count { it.exerciseLogId == l.id && (it.reps != null || it.durationMin != null) } })
        }
        val merged = formaItems.mapNotNull { it.watch?.id }.toSet()
        val watchItems = watch.filter { it.id !in merged }.map { w ->
            HistoryItem(w.title, LocalDate.ofEpochDay(w.epochDay), w.endTime ?: w.createdAt, null, w, 0)
        }
        val history = (formaItems + watchItems).sortedByDescending { it.endedAt }.take(10)
        val prog = cat.activeProgram
        WorkoutState(
            today, date, plan, workout, date == today && todayOverride != null && todayOverride.id != plan.workout?.id, session, slots, week,
            completedThisWeek = week.count { it.mark == DayMark.DONE }, requiredThisWeek = cat.requiredInWeekOf(today),
            programName = prog?.entity?.name ?: "No active program", programWeek = cat.weekOf(today), programWeeks = prog?.weeks ?: 1,
            history = history, catalog = cat,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun select(date: LocalDate) { viewDate.value = date }

    /** Do [workoutId] today instead of what's scheduled (doesn't change the schedule). */
    fun doToday(workoutId: String?) {
        val today = c.time.today()
        c.workoutOverride.value = workoutId?.let { today to it }
        viewDate.value = today
    }

    private suspend fun sessionId(): Long {
        val s = state.value ?: error("Not ready")
        return c.workouts.ensureSession(c.time.today(), s.workout!!.id)
    }

    fun start() = viewModelScope.launch { c.workouts.startTimer(sessionId()) }
    fun toggle(slot: String, done: Boolean) = viewModelScope.launch { c.workouts.setCompleted(sessionId(), slot, done) }
    fun substitute(slot: String, ex: String, remember: Boolean) = viewModelScope.launch { c.workouts.substitute(sessionId(), slot, ex, remember) }
    fun forget(slot: String) = viewModelScope.launch { c.workouts.clearRememberedSubstitution(slot) }
    fun logSet(slot: String, idx: Int, w: Double?, reps: Int?, min: Double?) = viewModelScope.launch { c.workouts.logSet(sessionId(), slot, idx, w, reps, min) }
    fun notes(slot: String, n: String) = viewModelScope.launch { c.workouts.setExerciseNotes(sessionId(), slot, n) }
    fun sessionNotes(n: String) = viewModelScope.launch { c.workouts.setSessionNotes(sessionId(), n) }
    fun finish(minutes: Int? = null) = viewModelScope.launch { c.setTimers.cancel(); c.workouts.finish(sessionId(), minutes) }
    fun pause() = viewModelScope.launch { c.workouts.pause(sessionId()) }
    fun resume() = viewModelScope.launch { c.workouts.resume(sessionId()) }
    fun setDuration(minutes: Int) = viewModelScope.launch { runCatching { c.workouts.setDuration(sessionId(), minutes) } }
    fun now() = c.time.nowMillis()

    val timer = c.setTimers.current
    val tick = c.setTimers.tick
    fun restFor(ss: SlotState) = TimerAdvice.recommendedRest(ss.slot.restSeconds, ss.kind, ss.exercise.tracksWeight, ss.slot.timed)
    fun startHold(ss: SlotState, idx: Int, seconds: Int) = viewModelScope.launch {
        c.setTimers.startHold(sessionId(), ss.slot.slotKey, ss.exercise.name, idx, ss.slot.sets, seconds, TimerAdvice.perSide(ss.slot.target), restFor(ss))
    }
    fun startRest(ss: SlotState, idx: Int) = viewModelScope.launch {
        c.setTimers.startRest(sessionId(), ss.slot.slotKey, ss.exercise.name, idx, ss.slot.sets, restFor(ss))
    }
    fun stopTimer() = c.setTimers.stop()
    fun cancelTimer() = c.setTimers.cancel()
    fun adjustRest(d: Int) = c.setTimers.adjustRest(d)
    fun reopen() = viewModelScope.launch { c.workouts.reopen(sessionId()) }
    fun discard(id: Long) = viewModelScope.launch { c.setTimers.cancel(); c.workouts.deleteSession(id) }
}

@Composable
fun WorkoutScreen(nav: NavController) {
    val vm = formaViewModel { WorkoutViewModel(it) }
    val st by vm.state.collectAsStateWithLifecycle()
    val s = st ?: return Box(Modifier.fillMaxSize())
    val c = Forma.colors
    var swapFor by remember { mutableStateOf<SlotState?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var pickToday by remember { mutableStateOf(false) }
    var finishCheck by remember { mutableStateOf<Int?>(null) }
    var editDuration by remember { mutableStateOf(false) }
    val isToday = s.viewDate == s.today
    val w = s.workout
    val active = isToday && s.session != null
    val timer by vm.timer.collectAsStateWithLifecycle()
    val tick by vm.tick.collectAsStateWithLifecycle()
    val view = androidx.compose.ui.platform.LocalView.current
    // Keep the screen awake while a hold or rest is counting down
    androidx.compose.runtime.DisposableEffect(timer != null) {
        view.keepScreenOn = timer != null
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = Space.gutter).padding(bottom = Space.xxl).testTag("workout"),
    ) {
        ScreenTitle("Workout", subtitle = s.programName + if (s.programWeeks > 1) " · week ${s.programWeek} of ${s.programWeeks}" else "")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            TextAction("Library", { nav.navigate(WorkoutRoutes.LIBRARY) }, modifier = Modifier.testTag("open_library"))
            TextAction("Schedule", { nav.navigate(WorkoutRoutes.SCHEDULE) }, modifier = Modifier.testTag("open_schedule"))
            TextAction("Import", { nav.navigate(WorkoutRoutes.IMPORT) }, color = c.textSecondary, modifier = Modifier.testTag("open_workout_import"))
        }
        Spacer(Modifier.height(Space.s))
        WeekStrip(s.week, s.today, s.viewDate) { vm.select(it) }
        Spacer(Modifier.height(Space.s))
        ThinProgress(if (s.requiredThisWeek == 0) 0f else s.completedThisWeek / s.requiredThisWeek.toFloat(), height = 3.dp)
        Spacer(Modifier.height(Space.xs))
        Text("${s.completedThisWeek} of ${s.requiredThisWeek} sessions this week", style = Forma.type.caption, color = c.textTertiary)

        Spacer(Modifier.height(Space.l))
        FormaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.relativeDay(s.viewDate, s.today).uppercase(), style = Forma.type.overline, color = c.textTertiary, modifier = Modifier.weight(1f))
                when {
                    s.session?.completedAt != null -> Tag("Completed", Tone.ACCENT)
                    s.overridden -> Tag("Swapped in today", Tone.WARN)
                    w == null && s.plan.rest -> Tag("Rest")
                    s.plan.optional -> Tag("Optional")
                    w != null -> Tag("${w.entity.durationMinutes} min")
                }
            }
            Spacer(Modifier.height(Space.xs))
            Text(w?.name ?: s.plan.title, style = Forma.type.titleSmall, color = c.text, modifier = Modifier.testTag("workout_title"))
            if (w != null) {
                Text(listOf(w.entity.category, w.entity.difficulty, w.entity.focus).filter { it.isNotBlank() }.joinToString(" · "),
                    style = Forma.type.bodySmall, color = c.textSecondary)
                if (w.entity.notes.isNotBlank()) { Spacer(Modifier.height(Space.m)); Text(w.entity.notes, style = Forma.type.bodySmall, color = c.textSecondary) }
                if (w.entity.tags.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) { w.entity.tags.forEach { Tag(it) } }
                }
            } else {
                Text(if (s.plan.rest) "No workout scheduled — recovery is part of the plan." else "Nothing is scheduled for this day.",
                    style = Forma.type.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (!isToday && w != null) TextAction("Do this workout today", { vm.doToday(w.id) }, modifier = Modifier.testTag("do_today"))
            if (isToday && s.session == null) TextAction(if (w == null) "Choose a workout for today" else "Do a different workout today", { pickToday = true },
                color = if (w == null) c.accent else c.textSecondary, modifier = Modifier.testTag("pick_today"))
            if (s.overridden && s.session == null) TextAction("Back to scheduled", { vm.doToday(null) }, color = c.textSecondary)
        }

        if (w != null) {
            if (isToday && s.session == null) {
                Spacer(Modifier.height(Space.s))
                PrimaryButton(if (s.plan.optional && !s.overridden) "Start optional session" else "Start workout", { vm.start() }, Modifier.fillMaxWidth().testTag("start_workout"))
            }
            if (!isToday && s.session == null && s.viewDate.isAfter(s.today)) {
                Spacer(Modifier.height(Space.s)); Note("Preview of ${Fmt.relativeDay(s.viewDate, s.today)}'s workout.")
            }
            if (active && s.session!!.completedAt == null) {
                Spacer(Modifier.height(Space.m))
                TimerCard(s.session, vm::now, onPause = { vm.pause() }, onResume = { vm.resume() })
            }
            SectionHeader("Exercises")
            s.slots.forEachIndexed { i, ss ->
                ExerciseCard(i + 1, ss, active = active, readOnly = !isToday && s.session != null,
                    onToggle = { vm.toggle(ss.slot.slotKey, !(ss.log?.completed ?: false)) },
                    onSwap = { swapFor = ss },
                    onSet = { idx, wt, r, m -> vm.logSet(ss.slot.slotKey, idx, wt, r, m) },
                    onNotes = { vm.notes(ss.slot.slotKey, it) },
                    timer = if (active) timer else null, restSeconds = vm.restFor(ss),
                    onHold = { idx, secs -> vm.startHold(ss, idx, secs) },
                    onRest = { idx -> vm.startRest(ss, idx) })
                Spacer(Modifier.height(Space.m))
            }
            if (active) {
                val sess = s.session!!
                var notes by remember(sess.id) { mutableStateOf(sess.notes ?: "") }
                FormaField(notes, { notes = it; vm.sessionNotes(it) }, "Session notes", singleLine = false)
                Spacer(Modifier.height(Space.m))
                if (sess.completedAt == null) {
                    PrimaryButton("Finish workout", {
                        val minutes = (sess.activeMinutes(vm.now()) ?: 0.0).toInt()
                        val planned = w.entity.durationMinutes
                        // Very long times usually mean the phone was put down mid-workout — ask before saving.
                        if (minutes > maxOf(planned * 2, 120)) finishCheck = minutes else vm.finish()
                    }, Modifier.fillMaxWidth().testTag("finish_workout"))
                } else {
                    Note("Workout complete${durationText(sess)?.let { " in $it" } ?: ""}. This counts toward your weekly total.", Tone.ACCENT)
                    TextAction("Edit duration", { editDuration = true }, modifier = Modifier.testTag("edit_duration"))
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
                    Row(Modifier.fillMaxWidth().clickable { vm.select(h.date) }.padding(vertical = Space.s).testTag("history_$i")) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(h.title, style = Forma.type.body, color = c.text)
                                if (h.watch != null) { Spacer(Modifier.width(Space.s)); Tag(if (h.forma == null) "Watch" else "+ watch data") }
                            }
                            val w = h.watch
                            Text(listOfNotNull(
                                h.forma?.let { durationText(it) } ?: w?.let { "${it.durationMinutes.toInt()} min" },
                                if (h.setsLogged > 0) "${h.setsLogged} sets" else null,
                                w?.avgHr?.let { "avg ${it.toInt()} bpm" }, w?.maxHr?.let { "max ${it.toInt()}" },
                                w?.kcal?.let { "${it.toInt()} kcal" }, w?.distanceM?.takeIf { it > 0 }?.let { "${Fmt.one(it / 1609.344)} mi" },
                                w?.dataOrigin?.let { com.forma.app.ui.metrics.appLabel(it) },
                            ).joinToString(" · ").ifEmpty { "Completed" }, style = Forma.type.caption, color = c.textTertiary)
                        }
                        Text(Fmt.relativeDay(h.date, s.today), style = Forma.type.caption, color = c.textTertiary)
                    }
                }
            }
            Text("Watch workouts from Health Connect are shown here but never change your schedule.", style = Forma.type.caption, color = c.textTertiary,
                modifier = Modifier.padding(top = Space.xs))
        }
        if (timer != null) Spacer(Modifier.height(200.dp))
    }
        val t = timer; val k = tick
        if (t != null && k != null && active) {
            TimerPanel(t, k, onStop = { vm.stopTimer() }, onCancel = { vm.cancelTimer() }, onAdjust = { vm.adjustRest(it) },
                modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    swapFor?.let { ss ->
        SubstitutionSheet(ss, onDismiss = { swapFor = null }, onPick = { ex, remember ->
            vm.substitute(ss.slot.slotKey, ex, remember); swapFor = null
        }, onForget = { vm.forget(ss.slot.slotKey); swapFor = null })
    }
    finishCheck?.let { measured ->
        val planned = w?.entity?.durationMinutes ?: 50
        var text by remember { mutableStateOf(planned.toString()) }
        FormaSheet({ finishCheck = null }, "That's $measured minutes", "Longer than expected for a ${planned}-minute workout — maybe the phone was set down mid-session. How long did you actually train?") {
            FormaField(text, { text = it }, "Workout length", suffix = "min", numeric = true, decimal = false, modifier = Modifier.testTag("finish_minutes"))
            Spacer(Modifier.height(Space.l))
            PrimaryButton("Save ${text.toIntOrNull() ?: planned} min", { vm.finish(text.toIntOrNull()?.coerceIn(1, 600) ?: planned); finishCheck = null },
                Modifier.fillMaxWidth().testTag("finish_save_minutes"), enabled = text.toIntOrNull() in 1..600)
            Spacer(Modifier.height(Space.s))
            SecondaryButton("Keep $measured min", { vm.finish(); finishCheck = null }, Modifier.fillMaxWidth())
        }
    }
    if (editDuration && s.session != null) {
        var text by remember { mutableStateOf(((s.session.activeMinutes(vm.now()) ?: 0.0).toInt()).toString()) }
        FormaSheet({ editDuration = false }, "Edit duration", "Correct how long this workout took. Paused time is already excluded.") {
            FormaField(text, { text = it }, "Workout length", suffix = "min", numeric = true, decimal = false, modifier = Modifier.testTag("duration_input"))
            Spacer(Modifier.height(Space.l))
            PrimaryButton("Save", { text.toIntOrNull()?.let { vm.setDuration(it) }; editDuration = false },
                Modifier.fillMaxWidth().testTag("duration_save"), enabled = text.toIntOrNull() in 1..600)
        }
    }
    if (pickToday) WorkoutPickerSheet(s.catalog, "Workout for today", { pickToday = false }) { vm.doToday(it); pickToday = false }
    if (confirmDiscard && s.session != null) {
        ConfirmDialog("Discard session?", "All checkmarks, sets and notes for today's ${w?.name} will be deleted.", "Discard",
            onConfirm = { vm.discard(s.session.id); confirmDiscard = false }, onDismiss = { confirmDiscard = false }, destructive = true)
    }
}

private fun durationText(s: WorkoutSessionEntity): String? {
    val end = s.completedAt ?: return null
    val m = (s.activeMinutes(end) ?: return null).toInt()
    return if (m in 1..600) "$m min" else null
}

/** Live elapsed time with Pause / Resume. Paused time isn't counted. */
@Composable
private fun TimerCard(session: WorkoutSessionEntity, now: () -> Long, onPause: () -> Unit, onResume: () -> Unit) {
    val c = Forma.colors
    var tick by remember { mutableStateOf(now()) }
    val paused = session.pausedAt != null
    androidx.compose.runtime.LaunchedEffect(session.id, paused) {
        while (!paused) { tick = now(); kotlinx.coroutines.delay(1000) }
        tick = now()
    }
    val secs = ((session.activeMinutes(tick) ?: 0.0) * 60).toLong()
    FormaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (paused) "Paused" else if (session.startedAt == null) "Ready" else "Elapsed", style = Forma.type.caption,
                    color = if (paused) c.warn else c.textSecondary)
                Text("%d:%02d".format(secs / 60, secs % 60), style = Forma.type.numberLarge, color = if (paused) c.textTertiary else c.text,
                    modifier = Modifier.testTag("workout_timer"))
            }
            if (paused) PrimaryButton("Resume", onResume, Modifier.testTag("resume_workout"))
            else SecondaryButton("Pause", onPause, Modifier.testTag("pause_workout"))
        }
    }
}

@Composable
private fun WeekStrip(week: List<WeekDayState>, today: LocalDate, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val c = Forma.colors
    Row(Modifier.fillMaxWidth()) {
        week.forEach { d ->
            val sel = d.date == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.m))
                    .then(if (sel) Modifier.background(c.surface).border(1.dp, c.line, RoundedCornerShape(Radius.m)) else Modifier)
                    .clickable { onSelect(d.date) }.padding(vertical = Space.s).testTag("day_${d.date.dayOfWeek.name.take(3)}"),
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
    n: Int, ss: SlotState, active: Boolean, readOnly: Boolean, onToggle: () -> Unit, onSwap: () -> Unit,
    onSet: (Int, Double?, Int?, Double?) -> Unit, onNotes: (String) -> Unit,
    timer: SetTimer? = null, restSeconds: Int = 0, onHold: (Int, Int) -> Unit = { _, _ -> }, onRest: (Int) -> Unit = {},
) {
    val c = Forma.colors
    var expanded by rememberSaveable(ss.slot.slotKey) { mutableStateOf(false) }
    val done = ss.log?.completed == true
    val key = ss.slot.slotKey
    FormaCard(padding = androidx.compose.foundation.layout.PaddingValues(start = Space.xs, end = Space.l, top = Space.s, bottom = Space.m)) {
        Row(verticalAlignment = Alignment.Top) {
            if (active) CheckCircle(done, onToggle, "Mark ${ss.exercise.name} complete", Modifier.testTag("check_$key"))
            else Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Text(if (done) "✓" else "$n", style = Forma.type.numberSmall, color = if (done) c.accent else c.textTertiary)
            }
            Column(Modifier.weight(1f).padding(top = 10.dp).clickable { expanded = !expanded }) {
                Text(ss.exercise.name, style = Forma.type.heading, color = if (done) c.textSecondary else c.text, modifier = Modifier.testTag("exname_$key"))
                Text(
                    "${if (ss.slot.sets > 1) "${ss.slot.sets} × " else ""}${ss.slot.target}" +
                        (if (ss.slot.restSeconds > 0) " · rest ${restLabel(ss.slot.restSeconds)}"
                        else if (ss.slot.sets > 1 && restSeconds > 0) " · rest ${restLabel(restSeconds)} (recommended)" else ""),
                    style = Forma.type.bodySmall, color = c.textSecondary,
                )
                if (ss.substituted) Text("Substitute for ${ss.originalName}${if (ss.remembered) " · remembered" else ""}", style = Forma.type.caption, color = c.warn)
            }
            if (!readOnly && ss.substitutes.size > 1) TextAction("Swap", onSwap, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp).testTag("swap_$key"))
        }
        Column(Modifier.padding(start = 44.dp)) {
            val instruction = if (ss.substituted) ss.exercise.instruction else ss.slot.instructionOverride ?: ss.exercise.instruction
            if (instruction.isNotBlank()) Text(instruction, style = Forma.type.caption, color = c.textTertiary, maxLines = if (expanded) 12 else 2)
            if (expanded) ss.exercise.equipment?.let { Text("Equipment: $it", style = Forma.type.caption, color = c.textTertiary) }
            if (expanded && ss.exercise.muscleGroups.isNotEmpty()) Text("Muscles: ${ss.exercise.muscleGroups.joinToString()}", style = Forma.type.caption, color = c.textTertiary)
            ss.advice?.let { a -> Spacer(Modifier.height(Space.s)); Text(a.message, style = Forma.type.caption, color = c.accent) }
            if (ss.lastSets.isNotEmpty() && !readOnly) {
                Text("Last (${ss.lastDate?.let { Fmt.shortDate(it) }}): " + ss.lastSets.joinToString("  ") { s ->
                    if (s.durationMin != null) durLabel(s.durationMin) else "${s.weightLb?.let { Fmt.amount(it) + "×" } ?: ""}${s.reps ?: "–"}"
                }, style = Forma.type.caption, color = c.textTertiary)
            }
            if (readOnly && ss.sets.isNotEmpty()) {
                Text("Logged: " + ss.sets.joinToString("  ") { s ->
                    if (s.durationMin != null) durLabel(s.durationMin) else "${s.weightLb?.let { Fmt.amount(it) + "×" } ?: ""}${s.reps ?: "–"}"
                }, style = Forma.type.caption, color = c.textSecondary)
            }
            if (active) {
                Spacer(Modifier.height(Space.s))
                val tracksSets = ss.kind == ExerciseKind.STRENGTH || (ss.kind == ExerciseKind.CORE && ss.exercise.tracksWeight)
                when {
                    ss.kind == ExerciseKind.CARDIO -> SetRow(ss, 0, onSet)
                    TimerAdvice.hasHoldTimer(ss.slot.timed, ss.kind, ss.slot.target) -> HoldSets(ss, timer, onHold)
                    ss.slot.timed || !tracksSets && ss.slot.repMin == null ->
                        if (ss.slot.sets > 1) RestOnly(ss, timer, onRest)
                    else -> (0 until ss.slot.sets).forEach { idx ->
                        SetRow(ss, idx, onSet, onRest = { onRest(idx) },
                            resting = timer?.kind == TimerKind.REST && timer.slotKey == key && timer.setIndex == idx)
                    }
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
private fun SetRow(ss: SlotState, idx: Int, onSet: (Int, Double?, Int?, Double?) -> Unit, onRest: (() -> Unit)? = null, resting: Boolean = false) {
    val c = Forma.colors
    val existing = ss.sets.firstOrNull { it.setIndex == idx }
    val last = ss.lastSets.firstOrNull { it.setIndex == idx }
    val key = "${ss.log?.id}-${ss.exercise.key}-$idx"
    val tag = ss.slot.slotKey
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ss.kind == ExerciseKind.CARDIO) "Time" else "Set ${idx + 1}", style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.width(44.dp))
        if (ss.kind == ExerciseKind.CARDIO || ss.slot.timed) {
            var min by remember(key) { mutableStateOf(existing?.durationMin?.let { Fmt.amount(it) } ?: "") }
            FormaField(min, { min = it; onSet(idx, null, null, it.toDoubleOrNull()) }, "Minutes", Modifier.weight(1f).testTag("min_${tag}_$idx"),
                numeric = true, placeholder = last?.durationMin?.let { Fmt.amount(it) })
        } else {
            var w by remember(key) { mutableStateOf(existing?.weightLb?.let { Fmt.amount(it) } ?: "") }
            var r by remember(key) { mutableStateOf(existing?.reps?.toString() ?: "") }
            if (ss.exercise.tracksWeight) {
                FormaField(w, { w = it; onSet(idx, it.toDoubleOrNull(), r.toIntOrNull(), null) }, "lb", Modifier.weight(1f).testTag("w_${tag}_$idx"),
                    numeric = true, placeholder = last?.weightLb?.let { Fmt.amount(it) })
                Spacer(Modifier.width(Space.s))
            }
            FormaField(r, { r = it; onSet(idx, w.toDoubleOrNull(), it.toIntOrNull(), null) }, "reps", Modifier.weight(1f).testTag("r_${tag}_$idx"),
                numeric = true, decimal = false, placeholder = last?.reps?.toString())
            if (onRest != null) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRest).testTag("rest_${tag}_$idx"), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Timer, "Set done — start rest",
                        tint = if (resting) c.accent else c.textSecondary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

private fun durLabel(min: Double) = if (min < 1.0) "${Math.round(min * 60)} s" else if (min < 10 && Math.round(min * 60) % 60 != 0L) TimerAdvice.label(Math.round(min * 60).toInt()) else "${Fmt.amount(min)} min"

/** Timed holds (planks, side holds, mobility): one Start per set, with a hold length you can nudge. */
@Composable
private fun HoldSets(ss: SlotState, timer: SetTimer?, onHold: (Int, Int) -> Unit) {
    val c = Forma.colors
    val key = ss.slot.slotKey
    val range = TimerAdvice.holdRange(ss.slot.target)
    val perSide = TimerAdvice.perSide(ss.slot.target)
    val lastSec = ss.lastSets.mapNotNull { it.durationMin }.maxOrNull()?.let { Math.round(it * 60).toInt() }
    var secs by rememberSaveable(key, ss.exercise.key) { mutableStateOf(TimerAdvice.defaultHold(ss.slot.target, lastSec)) }
    val step = if ((range?.second ?: 60) > 120) 30 else 5
    val busy = timer?.kind == TimerKind.HOLD
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Hold", style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.width(44.dp))
        TextAction("−", { secs = (secs - step).coerceAtLeast(5) }, color = c.textSecondary, modifier = Modifier.testTag("hold_minus_$key"))
        Text(TimerAdvice.label(secs) + if (perSide) " each side" else "", style = Forma.type.number, color = c.text, modifier = Modifier.testTag("hold_len_$key"))
        TextAction("+", { secs = (secs + step).coerceAtMost(600) }, color = c.textSecondary, modifier = Modifier.testTag("hold_plus_$key"))
    }
    val next = (0 until ss.slot.sets).firstOrNull { i -> ss.sets.none { it.setIndex == i && it.durationMin != null } }
    (0 until ss.slot.sets).forEach { idx ->
        val logged = ss.sets.firstOrNull { it.setIndex == idx }?.durationMin
        val running = timer != null && timer.slotKey == key && timer.setIndex == idx
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Set ${idx + 1}", style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.width(44.dp))
            Text(
                when {
                    running && timer!!.kind == TimerKind.HOLD -> "Holding…"
                    running && timer!!.kind == TimerKind.REST -> "✓ ${logged?.let(::durLabel) ?: ""} · resting"
                    logged != null -> "✓ ${durLabel(logged)}"
                    else -> "—"
                },
                style = Forma.type.body, color = if (logged != null) c.accent else c.textTertiary, modifier = Modifier.weight(1f).testTag("hold_done_${key}_$idx"),
            )
            if (!busy) TextAction(if (logged == null) "Start" else "Redo", { onHold(idx, secs) },
                color = if (idx == next) c.accent else c.textSecondary, modifier = Modifier.testTag("hold_${key}_$idx"))
        }
    }
}

/** Untracked multi-set exercises still get a rest timer. */
@Composable
private fun RestOnly(ss: SlotState, timer: SetTimer?, onRest: (Int) -> Unit) {
    val c = Forma.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Between sets", style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.weight(1f))
        if (timer == null) TextAction("Start rest", { onRest(0) }, modifier = Modifier.testTag("rest_${ss.slot.slotKey}_0"))
    }
}

/** Pinned to the bottom of the workout screen while a hold or rest is running. */
@Composable
private fun TimerPanel(t: SetTimer, k: TimerTick, onStop: () -> Unit, onCancel: () -> Unit, onAdjust: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Forma.colors
    val secs = (k.remainingMs + 999) / 1000
    val phase = k.phase
    val hold = t.kind == TimerKind.HOLD
    val holding = phase?.holding == true
    val shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l)
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape)
            .padding(horizontal = Space.gutter, vertical = Space.m).testTag("set_timer"),
    ) {
        Text(
            if (hold) "${t.exerciseName} · set ${t.setIndex + 1} of ${t.totalSets}"
            else "Rest · " + if (t.isLastSet) "next exercise" else "then set ${t.setIndex + 2} of ${t.totalSets} — ${t.exerciseName}",
            style = Forma.type.caption, color = c.textSecondary, maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(phase?.label ?: "Done", style = Forma.type.heading, color = when { holding -> c.accent; hold -> c.warn; else -> c.text },
                    modifier = Modifier.testTag("timer_phase"))
                Text("%d:%02d".format(secs / 60, secs % 60), style = Forma.type.numberLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(44f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = c.text, modifier = Modifier.testTag("timer_remaining"))
            }
            if (hold) {
                Column(horizontalAlignment = Alignment.End) {
                    PrimaryButton(if (holding) "Done — save ${k.heldSeconds / maxOf(1, t.phases.count { it.holding })} s" else "Stop", onStop, Modifier.testTag("timer_stop"))
                    TextAction("Cancel", onCancel, color = c.textTertiary, modifier = Modifier.testTag("timer_cancel"))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    TextAction("−15", { onAdjust(-15) }, color = c.textSecondary, modifier = Modifier.testTag("rest_minus"))
                    TextAction("+15", { onAdjust(15) }, color = c.textSecondary, modifier = Modifier.testTag("rest_plus"))
                    PrimaryButton("Skip", onCancel, Modifier.testTag("rest_skip"))
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        val len = (phase?.seconds ?: 1) * 1000f
        ThinProgress(if (phase == null) 1f else (1f - k.remainingMs / len).coerceIn(0f, 1f))
    }
}

internal fun restLabel(s: Int) = if (s >= 60 && s % 60 == 0) "${s / 60} min" else if (s >= 60) "${s / 60}:${"%02d".format(s % 60)}" else "$s s"

@Composable
private fun SubstitutionSheet(ss: SlotState, onDismiss: () -> Unit, onPick: (String, Boolean) -> Unit, onForget: () -> Unit) {
    val c = Forma.colors
    var remember by remember { mutableStateOf(ss.remembered) }
    FormaSheet(onDismiss, "Swap ${ss.originalName.lowercase()}",
        if (ss.slot.alternatives.isNotEmpty()) "Approved alternatives for this workout." else "Alternatives train the same muscles or movement pattern.") {
        ss.substitutes.forEachIndexed { i, ex ->
            val current = ex.key == ss.exercise.key
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).clickable { onPick(ex.key, remember) }
                    .padding(vertical = Space.m).testTag("sub_${ex.key}"),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.name, style = Forma.type.bodyStrong, color = c.text)
                        if (i == 0) { Spacer(Modifier.width(Space.s)); Tag("Original") }
                        if (current) { Spacer(Modifier.width(Space.s)); Tag("Current", Tone.ACCENT) }
                    }
                    if (ex.instruction.isNotBlank()) Text(ex.instruction, style = Forma.type.caption, color = c.textTertiary)
                }
            }
            Hairline()
        }
        Spacer(Modifier.height(Space.s))
        SwitchRow("Use for future workouts", "Remember this choice for this exercise in this workout.", remember, { remember = it })
        if (ss.remembered) TextAction("Stop remembering a substitute", onForget, color = c.textSecondary)
    }
}

/** Pick any workout from the library. */
@Composable
fun WorkoutPickerSheet(catalog: WorkoutCatalog, title: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = Forma.colors
    FormaSheet(onDismiss, title, "From your workout library.") {
        catalog.workouts.groupBy { it.entity.category }.toSortedMap().forEach { (cat, ws) ->
            Text(cat.uppercase(), style = Forma.type.overline, color = c.textTertiary, modifier = Modifier.padding(top = Space.m, bottom = Space.xs))
            ws.forEach { w ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).clickable { onPick(w.id) }.padding(vertical = Space.s).testTag("pick_${w.id}")) {
                    Text(w.name, style = Forma.type.bodyStrong, color = c.text)
                    Text("${w.entity.difficulty} · ${w.entity.durationMinutes} min · ${w.slots.size} exercises", style = Forma.type.caption, color = c.textTertiary)
                }
            }
        }
    }
}
