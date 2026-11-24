package com.forma.app.ui.workout

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.importer.ImportReport
import com.forma.app.data.importer.ImportStatus
import com.forma.app.data.importer.RecipeImportFormat
import com.forma.app.data.workout.WorkoutCandidate
import com.forma.app.data.workout.WorkoutCatalog
import com.forma.app.data.workout.WorkoutImportPlan
import com.forma.app.data.workout.WorkoutImportSpec
import com.forma.app.data.workout.WorkoutItemKind
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.CheckCircle
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.ListRow
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.SwitchRow
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.recipes.ImportReportView
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

class WorkoutImportViewModel(private val c: AppContainer) : ViewModel() {
    val plan = MutableStateFlow<WorkoutImportPlan?>(null)
    val report = MutableStateFlow<ImportReport?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun analyze(text: String) = viewModelScope.launch {
        if (text.isBlank()) { message.value = "Nothing to import — paste or choose a file first."; return@launch }
        busy.value = true; message.value = null
        plan.value = c.workoutImporter.analyze(text)
        busy.value = false
    }

    fun analyzeUri(context: Context, uri: android.net.Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull() }
        if (text == null) message.value = "Couldn't read that file." else analyze(text)
    }

    fun update(index: Int, f: (WorkoutCandidate) -> WorkoutCandidate) {
        val p = plan.value ?: return
        plan.value = p.copy(candidates = p.candidates.mapIndexed { i, x -> if (i == index) f(x) else x })
    }

    fun approveUpdates() {
        val p = plan.value ?: return
        plan.value = p.copy(candidates = p.candidates.map { if (it.status == ImportStatus.UPDATE) it.copy(include = true) else it })
    }

    fun commit() = viewModelScope.launch {
        val p = plan.value ?: return@launch
        busy.value = true
        report.value = c.workoutImporter.commit(p, p.candidates, c.library)
        busy.value = false
    }

    fun reset() { plan.value = null; report.value = null; message.value = null }

    fun export(context: Context, uri: android.net.Uri, onlyMine: Boolean) = viewModelScope.launch {
        message.value = runCatching {
            val text = if (onlyMine) {
                val ws = c.db.library().workouts().filter { !it.builtIn }.map { it.id }
                val ps = c.db.library().programs().filter { !it.builtIn }.map { it.id }
                if (ws.isEmpty() && ps.isEmpty()) error("You have no imported or custom workouts yet")
                c.workoutImporter.export(ws, ps)
            } else c.workoutImporter.export(null, null)
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
        }.fold({ "Exported in forma.workout v1 format." }, { "Export failed: ${it.message}" })
    }
}

@Composable
fun WorkoutImportScreen(nav: NavController) {
    val vm = formaViewModel { WorkoutImportViewModel(it) }
    val plan by vm.plan.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("workout_import")) {
        BackBar(when { report != null -> "Import report"; plan != null -> "Review import"; else -> "Import workouts" }, {
            if (plan != null && report == null) vm.reset() else nav.popBackStack()
        })
        when {
            report != null -> ImportReportView(report!!, { nav.popBackStack() }, { vm.reset() }, context,
                successNote = "Imported workouts are in your library. Your schedule only changed where you chose to schedule or activate something.")
            plan != null -> Preview(plan!!, busy, vm)
            else -> Choose(vm, message, busy, context)
        }
    }
}

@Composable
private fun Choose(vm: WorkoutImportViewModel, message: String?, busy: Boolean, context: Context) {
    val c = Forma.colors
    var text by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.analyzeUri(context, uri) }
    val exportMine = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(context, uri, true) }
    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(context, uri, false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
        Text("Import single workouts, a weekly program or a multi-week plan. You review everything first, and nothing changes your schedule unless you choose it.",
            style = Forma.type.bodySmall, color = c.textSecondary)
        message?.let { Spacer(Modifier.height(Space.m)); Note(it, if (it.startsWith("Exported")) Tone.ACCENT else Tone.WARN) }
        SectionHeader("From a file")
        FormaCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            ListRow("Import JSON file", "A forma.workout file", Icons.Outlined.FileUpload, { open.launch(arrayOf("application/json", "text/plain", "*/*")) })
        }
        SectionHeader("Paste")
        FormaField(text, { text = it }, "{ \"schema\": \"forma.workout\", … }", Modifier.heightIn(min = 160.dp).testTag("wpaste_input"), singleLine = false)
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            SecondaryButton("Paste", {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text = it }
            }, Modifier.weight(1f), icon = Icons.Outlined.ContentPaste)
            PrimaryButton(if (busy) "Checking…" else "Check", { vm.analyze(text) }, Modifier.weight(1f).testTag("wcheck"), enabled = !busy && text.isNotBlank())
        }
        SectionHeader("For ChatGPT")
        FormaCard {
            Text("Copy the FORMA workout format, paste it into ChatGPT, then ask — e.g. \"an 8-week ski-prep program, Sunday–Thursday, 50-minute sessions\".",
                style = Forma.type.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(Space.m))
            SecondaryButton(if (copied) "Copied" else "Copy format instructions", {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("FORMA workout format", WorkoutImportSpec.PROMPT)); copied = true
            }, Modifier.fillMaxWidth(), icon = Icons.Outlined.ContentCopy)
        }
        SectionHeader("Export")
        FormaCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            ListRow("Export my workouts & programs", "Imported and your own", Icons.Outlined.FileDownload, { exportMine.launch("forma-my-workouts-${LocalDate.now()}.json") })
            Hairline()
            ListRow("Export entire library", "Built-in workouts and program too", Icons.Outlined.Description, { exportAll.launch("forma-workouts-${LocalDate.now()}.json") })
        }
    }
}

@Composable
private fun Preview(plan: WorkoutImportPlan, busy: Boolean, vm: WorkoutImportViewModel) {
    val c = Forma.colors
    val cands = plan.candidates
    val selected = cands.count { it.include }
    val updates = cands.count { it.status == ImportStatus.UPDATE }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.testTag("wimport_list"), contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            item {
                Row {
                    StatBlock("Workouts", "${cands.count { it.parsed.kind == WorkoutItemKind.WORKOUT }}", null, Modifier.weight(1f))
                    StatBlock("Programs", "${cands.count { it.parsed.kind == WorkoutItemKind.PROGRAM }}", null, Modifier.weight(1f))
                    StatBlock("Updates", "$updates", null, Modifier.weight(1f))
                    StatBlock("Need fixing", "${cands.count { it.status == ImportStatus.INVALID }}", null, Modifier.weight(1f),
                        valueColor = if (cands.any { it.status == ImportStatus.INVALID }) c.negative else c.text)
                }
                plan.fileIssues.forEach { Spacer(Modifier.height(Space.s)); Note(it.message, if (it.severity == RecipeImportFormat.Severity.ERROR) Tone.NEGATIVE else Tone.NEUTRAL) }
                Spacer(Modifier.height(Space.s))
                Note("Imported items go to your library. Your schedule only changes if you choose \"Add to my schedule\" or \"Make active\" below.")
                if (updates > 0) TextAction("Approve all updates", { vm.approveUpdates() }, modifier = Modifier.testTag("wapprove_updates"))
            }
            itemsIndexed(cands, key = { i, _ -> i }) { i, cand -> CandidateCard(i, cand, vm) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg).navigationBarsPadding()) {
            Hairline()
            PrimaryButton(if (busy) "Importing…" else if (selected == 0) "Nothing selected" else "Import $selected item${if (selected == 1) "" else "s"}",
                { vm.commit() }, Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m).testTag("wconfirm"), enabled = selected > 0 && !busy)
        }
    }
}

@Composable
private fun CandidateCard(index: Int, cand: WorkoutCandidate, vm: WorkoutImportViewModel) {
    val c = Forma.colors
    val p = cand.parsed
    var expanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(false) }
    FormaCard(Modifier.testTag("wcand_$index"), padding = PaddingValues(start = Space.xs, end = Space.l, top = Space.s, bottom = Space.m)) {
        Row(verticalAlignment = Alignment.Top) {
            if (p.valid) CheckCircle(cand.include, { vm.update(index) { it.copy(include = !it.include) } }, "Include ${p.name}", Modifier.testTag("winclude_$index"))
            else Spacer(Modifier.width(44.dp))
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(p.name, style = Forma.type.heading, color = c.text)
                Spacer(Modifier.height(Space.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Tag(if (p.kind == WorkoutItemKind.WORKOUT) "Workout" else "Program")
                    Tag(cand.status.label, when (cand.status) {
                        ImportStatus.NEW -> Tone.ACCENT; ImportStatus.UPDATE -> Tone.WARN; ImportStatus.BUILT_IN_CONFLICT -> Tone.NEUTRAL; ImportStatus.INVALID -> Tone.NEGATIVE
                    })
                    p.workout?.let { Tag(it.category); Tag(it.difficulty); Tag("${it.durationMinutes} min") }
                    p.program?.let { Tag(if (it.weeks > 1) "${it.weeks} weeks" else "Weekly") }
                }
                p.workout?.let { Text("${p.slots.size} exercises" + (if (it.tags.isNotEmpty()) " · ${it.tags.joinToString(", ")}" else ""), style = Forma.type.caption, color = c.textSecondary) }
                when (cand.status) {
                    ImportStatus.UPDATE -> Text(if (cand.include) "Will replace \"${cand.existingName}\". Past sessions and logged sets are kept." else "Tick to replace \"${cand.existingName}\".",
                        style = Forma.type.caption, color = c.warn)
                    ImportStatus.BUILT_IN_CONFLICT -> Text("Built-in items aren't overwritten — this imports as a separate copy.", style = Forma.type.caption, color = c.textTertiary)
                    else -> {}
                }
            }
        }
        Column(Modifier.padding(start = 44.dp)) {
            cand.errors.forEach { Text("✕ ${it.path.substringAfter("]").removePrefix(".").ifEmpty { "item" }}: ${it.message}", style = Forma.type.caption, color = c.negative) }
            cand.warnings.forEach { Text("! ${it.path.substringAfter("]").removePrefix(".").let { s -> if (s.isEmpty()) "" else "$s: " }}${it.message}", style = Forma.type.caption, color = c.warn) }
            if (cand.infos.isNotEmpty()) {
                TextAction(if (notes) "Hide notes" else "${cand.infos.size} note${if (cand.infos.size == 1) "" else "s"}", { notes = !notes }, color = c.textTertiary)
                AnimatedVisibility(notes) { Column { cand.infos.forEach { Text("· ${it.message}", style = Forma.type.caption, color = c.textTertiary) } } }
            }
            if (p.valid && cand.include) {
                Spacer(Modifier.height(Space.s))
                if (p.kind == WorkoutItemKind.WORKOUT) {
                    Text("Add to my schedule (every week)", style = Forma.type.caption, color = c.textSecondary)
                    Spacer(Modifier.height(Space.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Chip("Library only", cand.scheduleOn == null, { vm.update(index) { it.copy(scheduleOn = null) } }, Modifier.testTag("wlib_$index"))
                        WorkoutCatalog.SUNDAY_FIRST.forEach { d ->
                            Chip(d.name.take(3).lowercase().replaceFirstChar(Char::uppercase), cand.scheduleOn == d,
                                { vm.update(index) { it.copy(scheduleOn = d) } }, Modifier.testTag("wday_${d.name.take(3)}_$index"))
                        }
                    }
                } else {
                    SwitchRow("Make this my active program", "Your current schedule stays as it is unless you turn this on.", cand.activate,
                        { on -> vm.update(index) { it.copy(activate = on) } })
                    if (cand.activate) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Chip("Start this week", !cand.startNextWeek, { vm.update(index) { it.copy(startNextWeek = false) } })
                        Chip("Start next week", cand.startNextWeek, { vm.update(index) { it.copy(startNextWeek = true) } })
                    }
                }
            }
            if (p.valid) {
                TextAction(if (expanded) "Hide preview" else "Preview", { expanded = !expanded })
                AnimatedVisibility(expanded) {
                    Column {
                        if (p.kind == WorkoutItemKind.WORKOUT) p.slots.forEachIndexed { k, s ->
                            val name = p.newExercises.firstOrNull { it.key == s.exerciseKey }?.name ?: s.exerciseKey.replace('_', ' ')
                            Text("${k + 1}. ${name.replaceFirstChar(Char::uppercase)} — ${if (s.sets > 1) "${s.sets} × " else ""}${s.target}, rest ${restLabel(s.restSeconds)}" +
                                (if (s.alternatives.isNotEmpty()) " · ${s.alternatives.size} alternatives" else ""), style = Forma.type.bodySmall, color = c.text)
                        } else p.days.sortedWith(compareBy({ it.week }, { it.dayOfWeek % 7 })).forEach { d ->
                            Text("${if (d.week == 0) "Every week" else "Week ${d.week}"} · ${DayOfWeek.of(d.dayOfWeek).name.lowercase().replaceFirstChar(Char::uppercase)}: " +
                                (if (d.rest) "Rest" else d.workoutId ?: "—") + if (d.optional) " (optional)" else "", style = Forma.type.bodySmall, color = c.text)
                        }
                    }
                }
            }
        }
    }
}
