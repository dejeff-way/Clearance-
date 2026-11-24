package com.forma.app.ui.workout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.forma.app.data.workout.ProgramDef
import com.forma.app.data.workout.ScheduleScope
import com.forma.app.data.workout.WorkoutCatalog
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.ConfirmDialog
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.TrendAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

object WorkoutRoutes {
    const val LIBRARY = "workout_library"
    const val SCHEDULE = "workout_schedule"
    const val IMPORT = "workout_import"
    fun workout(id: String) = "workout_detail/$id"
    fun program(id: String) = "program_detail/$id"
}

private fun dayName(d: DayOfWeek) = d.name.lowercase().replaceFirstChar { it.uppercase() }

private enum class LibTab { WORKOUTS, PROGRAMS }

@Composable
fun WorkoutLibraryScreen(nav: NavController) {
    val c = Forma.colors
    val container = LocalContainer.current
    val cat by container.catalog.collectAsStateWithLifecycle()
    val catalog = cat ?: return Box(Modifier.fillMaxSize())
    var tab by rememberSaveable { mutableStateOf(LibTab.WORKOUTS) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("workout_library")) {
        BackBar("Workout library", { nav.popBackStack() }) {
            TextAction("Import", { nav.navigate(WorkoutRoutes.IMPORT) })
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Segmented(LibTab.entries, tab, { if (it == LibTab.WORKOUTS) "Workouts (${catalog.workouts.size})" else "Programs (${catalog.programs.size})" }, { tab = it },
                Modifier.fillMaxWidth().testTag("library_tabs"))
            Spacer(Modifier.height(Space.s))
            Text(
                if (tab == LibTab.WORKOUTS) "Everything available to you. Saving a workout here never changes your schedule."
                else "Your active program decides what's scheduled each day. Switching keeps all past workouts.",
                style = Forma.type.caption, color = c.textTertiary,
            )
            if (tab == LibTab.WORKOUTS) {
                val cats = catalog.workouts.map { it.entity.category }.distinct().sorted()
                Spacer(Modifier.height(Space.m))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Chip("All", category == null, { category = null })
                    cats.forEach { k -> Chip(k, category == k, { category = k }) }
                }
                catalog.workouts.filter { category == null || it.entity.category == category }.groupBy { it.entity.category }.toSortedMap().forEach { (k, ws) ->
                    SectionHeader(k)
                    FormaCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
                        ws.forEachIndexed { i, w ->
                            if (i > 0) Hairline()
                            Column(Modifier.fillMaxWidth().clickable { nav.navigate(WorkoutRoutes.workout(w.id)) }.padding(vertical = Space.m).testTag("lib_${w.id}")) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(w.name, style = Forma.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                                    if (!w.entity.builtIn) Tag("Imported")
                                }
                                Text("${w.entity.difficulty} · ${w.entity.durationMinutes} min · ${w.slots.size} exercises" +
                                    (if (w.entity.tags.isNotEmpty()) " · ${w.entity.tags.take(3).joinToString(", ")}" else ""),
                                    style = Forma.type.caption, color = c.textTertiary)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(Space.m))
                catalog.programs.forEach { p ->
                    val isActive = catalog.active?.programId == p.id
                    FormaCard(Modifier.padding(bottom = Space.m).testTag("prog_${p.id}"), onClick = { nav.navigate(WorkoutRoutes.program(p.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.entity.name, style = Forma.type.heading, color = c.text, modifier = Modifier.weight(1f))
                            if (isActive) Tag("Active", Tone.ACCENT) else if (!p.entity.builtIn) Tag(if (p.entity.origin == "USER") "Yours" else "Imported")
                        }
                        Text("${if (p.weeks > 1) "${p.weeks} weeks" else "Weekly"} · ${trainingDays(p)} training days/week" +
                            (if (isActive && p.weeks > 1) " · now in week ${catalog.weekOf(LocalDate.now())}" else ""),
                            style = Forma.type.caption, color = c.textTertiary)
                        if (p.entity.description.isNotBlank()) Text(p.entity.description, style = Forma.type.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

private fun trainingDays(p: ProgramDef) = DayOfWeek.entries.count { d -> p.dayFor(1, d)?.let { !it.rest && !it.optional && it.workoutId != null } == true }

@Composable
fun WorkoutDetailScreen(nav: NavController, id: String) {
    val c = Forma.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cat by container.catalog.collectAsStateWithLifecycle()
    val catalog = cat ?: return Box(Modifier.fillMaxSize())
    val w = catalog.workoutsById[id] ?: return Box(Modifier.fillMaxSize())
    var scheduleSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                val text = container.workoutImporter.export(listOf(id), emptyList())
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
            }.fold({ "Exported." }, { "Export failed: ${it.message}" })
        }
    }
    val usedOn = catalog.activeProgram?.days?.filter { it.workoutId == id }?.map { DayOfWeek.of(it.dayOfWeek) }?.distinct().orEmpty()

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("workout_detail")) {
        BackBar(w.name, { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Tag(w.entity.category); Tag(w.entity.difficulty); Tag("${w.entity.durationMinutes} min")
                if (!w.entity.builtIn) Tag("Imported")
                w.entity.tags.forEach { Tag(it) }
            }
            Spacer(Modifier.height(Space.m))
            Text(w.name, style = Forma.type.title, color = c.text)
            if (w.entity.goals.isNotEmpty()) Text("Goals: ${w.entity.goals.joinToString()}", style = Forma.type.bodySmall, color = c.textSecondary)
            if (w.entity.notes.isNotBlank()) { Spacer(Modifier.height(Space.s)); Text(w.entity.notes, style = Forma.type.bodySmall, color = c.textSecondary) }
            Spacer(Modifier.height(Space.s))
            Text(if (usedOn.isEmpty()) "Not in your active schedule." else "Scheduled on ${usedOn.sortedBy { it.value % 7 }.joinToString { dayName(it) }} in your active program.",
                style = Forma.type.caption, color = if (usedOn.isEmpty()) c.textTertiary else c.accent)
            message?.let { Spacer(Modifier.height(Space.s)); Note(it, if (it.startsWith("Export") && !it.contains("failed")) Tone.ACCENT else Tone.WARN) }
            Spacer(Modifier.height(Space.l))
            PrimaryButton("Do this workout today", {
                container.workoutOverride.value = container.time.today() to id
                nav.popBackStack(com.forma.app.ui.nav.Routes.WORKOUT, false)
            }, Modifier.fillMaxWidth().testTag("detail_do_today"))
            Spacer(Modifier.height(Space.s))
            SecondaryButton("Add to my schedule", { scheduleSheet = true }, Modifier.fillMaxWidth().testTag("detail_schedule"))

            SectionHeader("Exercises")
            FormaCard {
                w.slots.forEachIndexed { i, s ->
                    if (i > 0) Hairline()
                    val ex = catalog.exercise(s.exerciseKey)
                    Column(Modifier.padding(vertical = Space.s)) {
                        Text("${i + 1}. ${ex.name}", style = Forma.type.bodyStrong, color = c.text)
                        Text("${if (s.sets > 1) "${s.sets} × " else ""}${s.target}${if (s.restSeconds > 0) " · rest ${restLabel(s.restSeconds)}" else ""}" +
                            (ex.equipment?.let { " · $it" } ?: ""), style = Forma.type.caption, color = c.textSecondary)
                        (s.instructionOverride ?: ex.instruction).takeIf { it.isNotBlank() }?.let { Text(it, style = Forma.type.caption, color = c.textTertiary) }
                        val subs = catalog.substitutesFor(s).drop(1)
                        if (subs.isNotEmpty()) Text("Alternatives: ${subs.joinToString { it.name }}", style = Forma.type.caption, color = c.textTertiary)
                        if (s.progression != "NONE" && s.repMin != null) Text(
                            if (s.progression == "LINEAR") "Progression: add ${Fmt.amount(s.incrementLb ?: ex.incrementLb)} lb after every successful session"
                            else "Progression: add ${Fmt.amount(s.incrementLb ?: ex.incrementLb)} lb once every set reaches ${s.repMax} reps",
                            style = Forma.type.caption, color = c.accent)
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                TextAction("Export JSON", { export.launch("${w.id}.json") })
                if (!w.entity.builtIn) TextAction("Delete", { confirmDelete = true }, color = c.negative, modifier = Modifier.testTag("detail_delete"))
            }
        }
    }
    if (scheduleSheet) ScheduleSheet(catalog, "Add ${w.name} to your schedule", onDismiss = { scheduleSheet = false }) { day, scope2 ->
        scope.launch {
            container.library.schedule(day, id, rest = false, optional = false, scope = scope2, catalog = catalog)
            scheduleSheet = false
            message = "Added to ${dayName(day.dayOfWeek)} ${if (scope2 == ScheduleScope.EVERY_WEEK) "every week" else "this week"}."
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${w.name}?", "It's removed from the library. Past sessions stay in your history.", "Delete", {
        confirmDelete = false
        scope.launch {
            runCatching { container.library.deleteWorkout(id) }.onSuccess { withContext(Dispatchers.Main.immediate) { nav.popBackStack() } }
                .onFailure { message = it.message }
        }
    }, { confirmDelete = false }, destructive = true)
}

/** Day + scope picker used when adding a library workout to the active schedule. */
@Composable
private fun ScheduleSheet(catalog: WorkoutCatalog, title: String, onDismiss: () -> Unit, onConfirm: (LocalDate, ScheduleScope) -> Unit) {
    val c = Forma.colors
    val today = LocalDate.now()
    val weekStart = TrendAnalyzer.weekStart(today)
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var scope by remember { mutableStateOf(ScheduleScope.EVERY_WEEK) }
    FormaSheet(onDismiss, title, "Replaces whatever is on that day in your active program.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            (0..6).map { weekStart.plusDays(it.toLong()) }.forEach { d ->
                Chip(dayName(d.dayOfWeek).take(3), d == day, { day = d }, Modifier.testTag("sched_day_${d.dayOfWeek.name.take(3)}"))
            }
        }
        day?.let { d ->
            Spacer(Modifier.height(Space.s))
            Text("Currently: ${catalog.planFor(d).title}", style = Forma.type.caption, color = c.textTertiary)
        }
        Spacer(Modifier.height(Space.m))
        Segmented(ScheduleScope.entries, scope, { if (it == ScheduleScope.EVERY_WEEK) "Every week" else "This week only" }, { scope = it }, Modifier.fillMaxWidth())
        if (catalog.activeProgram?.entity?.builtIn == true) {
            Spacer(Modifier.height(Space.s))
            Note("The built-in program isn't changed — your edits go into \"My program\", which becomes active with the same start date.")
        }
        Spacer(Modifier.height(Space.l))
        PrimaryButton("Save", { day?.let { onConfirm(it, scope) } }, Modifier.fillMaxWidth().testTag("sched_save"), enabled = day != null)
    }
}

@Composable
fun ProgramDetailScreen(nav: NavController, id: String) {
    val c = Forma.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cat by container.catalog.collectAsStateWithLifecycle()
    val catalog = cat ?: return Box(Modifier.fillMaxSize())
    val p = catalog.programs.firstOrNull { it.id == id } ?: return Box(Modifier.fillMaxSize())
    val isActive = catalog.active?.programId == id
    var week by remember { mutableIntStateOf(if (isActive) catalog.weekOf(LocalDate.now()) else 1) }
    var activateSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                val text = container.workoutImporter.export(emptyList(), listOf(id))
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
            }.fold({ "Exported with the workouts it uses." }, { "Export failed: ${it.message}" })
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("program_detail")) {
        BackBar(p.entity.name, { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (isActive) Tag("Active", Tone.ACCENT)
                Tag(if (p.weeks > 1) "${p.weeks} weeks" else "Weekly")
                if (!p.entity.builtIn) Tag(if (p.entity.origin == "USER") "Yours" else "Imported")
            }
            Spacer(Modifier.height(Space.m))
            Text(p.entity.name, style = Forma.type.title, color = c.text)
            if (p.entity.description.isNotBlank()) Text(p.entity.description, style = Forma.type.bodySmall, color = c.textSecondary)
            message?.let { Spacer(Modifier.height(Space.s)); Note(it, Tone.ACCENT) }
            Spacer(Modifier.height(Space.l))
            if (!isActive) PrimaryButton("Make this my active program", { activateSheet = true }, Modifier.fillMaxWidth().testTag("activate_program"))
            else Note("This is your active program${if (p.weeks > 1) " — week ${catalog.weekOf(LocalDate.now())} of ${p.weeks}" else ""}.", Tone.ACCENT)

            if (p.weeks > 1) {
                SectionHeader("Week")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    (1..p.weeks).forEach { wk -> Chip("$wk", wk == week, { week = wk }) }
                }
            }
            SectionHeader(if (p.weeks > 1) "Week $week" else "Schedule")
            FormaCard {
                WorkoutCatalog.SUNDAY_FIRST.forEachIndexed { i, d ->
                    if (i > 0) Hairline()
                    val row = p.dayFor(week, d)
                    val w = row?.workoutId?.let { catalog.workoutsById[it] }
                    Row(Modifier.fillMaxWidth().then(if (w != null) Modifier.clickable { nav.navigate(WorkoutRoutes.workout(w.id)) } else Modifier).padding(vertical = Space.s)) {
                        Text(dayName(d), style = Forma.type.body, color = c.textSecondary, modifier = Modifier.width(110.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w?.name ?: row?.label ?: if (row?.rest == true) "Rest" else "—", style = Forma.type.body, color = if (w != null) c.text else c.textTertiary)
                            if (row?.optional == true) Text("Optional", style = Forma.type.caption, color = c.textTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                TextAction("Export JSON", { export.launch("${p.id}.json") })
                if (!p.entity.builtIn) TextAction("Delete", { confirmDelete = true }, color = c.negative)
            }
        }
    }
    if (activateSheet) {
        var next by remember { mutableStateOf(false) }
        FormaSheet({ activateSheet = false }, "Switch to ${p.entity.name}?", "Your schedule changes from the chosen week. Every past workout, set and note is kept.") {
            Segmented(listOf(false, true), next, { if (it) "Start next week" else "Start this week" }, { next = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.l))
            PrimaryButton("Activate", {
                scope.launch {
                    container.library.activate(id, if (next) LocalDate.now().plusWeeks(1) else LocalDate.now())
                    activateSheet = false; message = "Activated."
                }
            }, Modifier.fillMaxWidth().testTag("confirm_activate"))
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${p.entity.name}?", "The program is removed${if (isActive) " and the FORMA Base Program becomes active" else ""}. Workouts and history are kept.", "Delete", {
        confirmDelete = false
        scope.launch { runCatching { container.library.deleteProgram(id) }.onSuccess { withContext(Dispatchers.Main.immediate) { nav.popBackStack() } }.onFailure { message = it.message } }
    }, { confirmDelete = false }, destructive = true)
}

/** Edit the active schedule day by day. */
@Composable
fun ScheduleScreen(nav: NavController) {
    val c = Forma.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val cat by container.catalog.collectAsStateWithLifecycle()
    val catalog = cat ?: return Box(Modifier.fillMaxSize())
    val today = LocalDate.now()
    val weekStart = TrendAnalyzer.weekStart(today)
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    val p = catalog.activeProgram

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("schedule")) {
        BackBar("My schedule", { nav.popBackStack() }) { TextAction("Programs", { nav.navigate(WorkoutRoutes.LIBRARY) }) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Text(p?.entity?.name ?: "No active program", style = Forma.type.titleSmall, color = c.text)
            Text(if (p != null && p.weeks > 1) "Week ${catalog.weekOf(today)} of ${p.weeks}" else "Repeats every week",
                style = Forma.type.caption, color = c.textTertiary)
            Spacer(Modifier.height(Space.m))
            FormaCard {
                (0..6).map { weekStart.plusDays(it.toLong()) }.forEachIndexed { i, d ->
                    if (i > 0) Hairline()
                    val plan = catalog.planFor(d)
                    Row(Modifier.fillMaxWidth().clickable { editing = d }.padding(vertical = Space.m).testTag("sched_${d.dayOfWeek.name.take(3)}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(110.dp)) {
                            Text(dayName(d.dayOfWeek), style = Forma.type.body, color = if (d == today) c.accent else c.textSecondary)
                            Text(Fmt.shortDate(d), style = Forma.type.caption, color = c.textTertiary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(plan.title, style = Forma.type.bodyStrong, color = if (plan.workout != null) c.text else c.textTertiary)
                            if (plan.optional) Text("Optional", style = Forma.type.caption, color = c.textTertiary)
                        }
                        Text("Change", style = Forma.type.label, color = c.accent)
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            Text("Imported workouts are never added here automatically. Add them from the library or this screen.", style = Forma.type.caption, color = c.textTertiary)
        }
    }
    editing?.let { d ->
        var scope2 by remember(d) { mutableStateOf(ScheduleScope.EVERY_WEEK) }
        var optional by remember(d) { mutableStateOf(catalog.planFor(d).optional) }
        FormaSheet({ editing = null }, "${dayName(d.dayOfWeek)} · ${Fmt.shortDate(d)}", "Currently: ${catalog.planFor(d).title}") {
            Segmented(ScheduleScope.entries, scope2, { if (it == ScheduleScope.EVERY_WEEK) "Every week" else "This week only" }, { scope2 = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Chip("Required", !optional, { optional = false }); Chip("Optional", optional, { optional = true })
            }
            if (catalog.activeProgram?.entity?.builtIn == true) { Spacer(Modifier.height(Space.s)); Note("Changes are saved to \"My program\"; the built-in program stays as it is.") }
            Spacer(Modifier.height(Space.m))
            SecondaryButton("Rest day", {
                scope.launch { container.library.schedule(d, null, rest = true, optional = false, scope = scope2, catalog = catalog); editing = null }
            }, Modifier.fillMaxWidth().testTag("sched_rest"))
            catalog.workouts.groupBy { it.entity.category }.toSortedMap().forEach { (k, ws) ->
                Text(k.uppercase(), style = Forma.type.overline, color = c.textTertiary, modifier = Modifier.padding(top = Space.m, bottom = Space.xs))
                ws.forEach { w ->
                    Column(Modifier.fillMaxWidth().clickable {
                        scope.launch { container.library.schedule(d, w.id, rest = false, optional = optional, scope = scope2, catalog = catalog); editing = null }
                    }.padding(vertical = Space.s).testTag("sched_pick_${w.id}")) {
                        Text(w.name, style = Forma.type.bodyStrong, color = c.text)
                        Text("${w.entity.difficulty} · ${w.entity.durationMinutes} min", style = Forma.type.caption, color = c.textTertiary)
                    }
                }
            }
        }
    }
}
