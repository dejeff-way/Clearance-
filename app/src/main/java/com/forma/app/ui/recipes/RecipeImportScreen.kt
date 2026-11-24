package com.forma.app.ui.recipes

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
import com.forma.app.data.importer.ImportCandidate
import com.forma.app.data.importer.ImportOutcome
import com.forma.app.data.importer.ImportPlan
import com.forma.app.data.importer.ImportReport
import com.forma.app.data.importer.ImportSpec
import com.forma.app.data.importer.ImportStatus
import com.forma.app.data.importer.RecipeImportFormat
import com.forma.app.data.repo.RecipeMath
import com.forma.app.data.repo.TOTAL_UNIT
import com.forma.app.data.seed.Category
import com.forma.app.data.seed.KosherStatus
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.CheckCircle
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.KosherTag
import com.forma.app.ui.components.ListRow
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.engine.Portion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

class RecipeImportViewModel(private val c: AppContainer) : ViewModel() {
    val plan = MutableStateFlow<ImportPlan?>(null)
    val report = MutableStateFlow<ImportReport?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun analyze(text: String, plainText: Boolean) = viewModelScope.launch {
        if (text.isBlank()) { message.value = "Nothing to import — paste or choose a file first."; return@launch }
        busy.value = true; message.value = null
        val looksJson = text.trimStart().let { it.startsWith("{") || it.startsWith("[") || it.startsWith("```") }
        plan.value = withContext(Dispatchers.Default) {
            if (plainText && !looksJson) c.recipeImporter.analyzeText(text) else c.recipeImporter.analyzeJson(text)
        }
        busy.value = false
    }

    fun analyzeUri(context: Context, uri: android.net.Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull() }
        if (text == null) message.value = "Couldn't read that file." else analyze(text, plainText = false)
    }

    fun setInclude(index: Int, include: Boolean) {
        val p = plan.value ?: return
        plan.value = p.copy(candidates = p.candidates.mapIndexed { i, c -> if (i == index) c.copy(include = include && c.parsed.valid) else c })
    }

    fun includeAllUpdates() {
        val p = plan.value ?: return
        plan.value = p.copy(candidates = p.candidates.map { if (it.status == ImportStatus.UPDATE) it.copy(include = true) else it })
    }

    fun fix(index: Int, category: String?, kosher: String?, kcal: Double?, protein: Double?) = viewModelScope.launch {
        val p = plan.value ?: return@launch
        val fixed = RecipeImportFormat.withFixes(p.candidates[index].parsed, category, kosher, kcal, protein)
        val cand = c.recipeImporter.reclassify(fixed)
        plan.value = p.copy(candidates = p.candidates.mapIndexed { i, x -> if (i == index) cand else x })
    }

    fun commit() = viewModelScope.launch {
        val p = plan.value ?: return@launch
        busy.value = true
        report.value = c.recipeImporter.commit(p, p.candidates)
        busy.value = false
    }

    fun reset() { plan.value = null; report.value = null; message.value = null }

    fun export(context: Context, uri: android.net.Uri, onlyMine: Boolean) = viewModelScope.launch {
        message.value = runCatching {
            val ids = if (onlyMine) c.db.recipes().all().filter { !it.builtIn }.map { it.id } else null
            if (ids != null && ids.isEmpty()) error("You have no custom or imported recipes yet")
            val text = c.recipeImporter.export(ids)
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
        }.fold({ "Recipes exported in forma.recipe v1 format." }, { "Export failed: ${it.message}" })
    }
}

private enum class PasteMode { JSON, TEXT }

@Composable
fun RecipeImportScreen(nav: NavController) {
    val vm = formaViewModel { RecipeImportViewModel(it) }
    val plan by vm.plan.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("recipe_import")) {
        BackBar(when { report != null -> "Import report"; plan != null -> "Review import"; else -> "Import recipes" }, {
            if (plan != null && report == null) vm.reset() else nav.popBackStack()
        })
        when {
            report != null -> ReportView(report!!, onDone = { nav.popBackStack() }, onAnother = { vm.reset() }, context)
            plan != null -> PreviewView(plan!!, busy, vm)
            else -> ChooseView(vm, message, busy, context)
        }
    }
}

@Composable
private fun ChooseView(vm: RecipeImportViewModel, message: String?, busy: Boolean, context: Context) {
    val c = Forma.colors
    var mode by remember { mutableStateOf(PasteMode.JSON) }
    var text by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.analyzeUri(context, uri) }
    val exportMine = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(context, uri, true) }
    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.export(context, uri, false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
        Text("Bring in one recipe or a whole collection. Everything is checked and shown to you before it's saved.",
            style = Forma.type.bodySmall, color = c.textSecondary)
        message?.let { Spacer(Modifier.height(Space.m)); Note(it, if (it.startsWith("Recipes exported")) Tone.ACCENT else Tone.WARN) }

        SectionHeader("From a file")
        FormaCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            ListRow("Import JSON file", "A forma.recipe file from your phone, Drive or email", Icons.Outlined.FileUpload,
                { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) })
        }

        SectionHeader("Paste")
        Segmented(PasteMode.entries, mode, { if (it == PasteMode.JSON) "Recipe data (JSON)" else "Recipe text" }, { mode = it },
            Modifier.fillMaxWidth().testTag("paste_mode"))
        Spacer(Modifier.height(Space.s))
        Text(
            if (mode == PasteMode.JSON) "Paste JSON generated by ChatGPT or exported from FORMA."
            else "Paste an ordinary written recipe. FORMA reads the title, ingredients, steps and any calories it finds; you'll confirm the rest.",
            style = Forma.type.caption, color = c.textTertiary,
        )
        Spacer(Modifier.height(Space.s))
        FormaField(text, { text = it }, if (mode == PasteMode.JSON) "{ \"schema\": \"forma.recipe\", … }" else "Recipe text",
            Modifier.heightIn(min = 160.dp).testTag("paste_input"), singleLine = false)
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            SecondaryButton("Paste", {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text = it }
            }, Modifier.weight(1f), icon = Icons.Outlined.ContentPaste)
            PrimaryButton(if (busy) "Checking…" else "Check", { vm.analyze(text, mode == PasteMode.TEXT) }, Modifier.weight(1f).testTag("check_import"),
                enabled = !busy && text.isNotBlank())
        }

        SectionHeader("For ChatGPT")
        FormaCard {
            Text("Copy the FORMA format instructions, paste them into ChatGPT, then ask for recipes — e.g. \"15 high-protein kosher suppers under 550 calories\". Paste its answer above.",
                style = Forma.type.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(Space.m))
            SecondaryButton(if (copied) "Copied" else "Copy format instructions", {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("FORMA recipe format", ImportSpec.PROMPT)); copied = true
            }, Modifier.fillMaxWidth().testTag("copy_prompt"), icon = Icons.Outlined.ContentCopy)
        }

        SectionHeader("Export")
        FormaCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            ListRow("Export my recipes", "Your created and imported recipes, with photos", Icons.Outlined.FileDownload,
                { exportMine.launch("forma-my-recipes-${LocalDate.now()}.json") })
            Hairline()
            ListRow("Export entire library", "Built-in recipes too", Icons.Outlined.Description,
                { exportAll.launch("forma-recipes-${LocalDate.now()}.json") })
        }
        Spacer(Modifier.height(Space.s))
        Text("Exports use the same forma.recipe v1 format, so they can be imported on another phone.", style = Forma.type.caption, color = c.textTertiary)
    }
}

@Composable
private fun PreviewView(plan: ImportPlan, busy: Boolean, vm: RecipeImportViewModel) {
    val c = Forma.colors
    val cands = plan.candidates
    val selected = cands.count { it.include }
    val updates = cands.count { it.status == ImportStatus.UPDATE }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.testTag("import_list"), contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            item {
                Row {
                    StatBlock("Recipes", "${cands.size}", null, Modifier.weight(1f))
                    StatBlock("New", "${cands.count { it.status == ImportStatus.NEW }}", null, Modifier.weight(1f))
                    StatBlock("Updates", "$updates", null, Modifier.weight(1f))
                    StatBlock("Need fixing", "${cands.count { it.status == ImportStatus.INVALID }}", null, Modifier.weight(1f),
                        valueColor = if (cands.any { it.status == ImportStatus.INVALID }) c.negative else c.text)
                }
                plan.fileIssues.forEach { Spacer(Modifier.height(Space.s)); Note(it.message, if (it.severity == RecipeImportFormat.Severity.ERROR) Tone.NEGATIVE else Tone.NEUTRAL) }
                if (updates > 0) {
                    Spacer(Modifier.height(Space.s))
                    Note("$updates recipe${if (updates == 1) " has" else "s have"} the same id as one in your library. They're only replaced if you tick them.", Tone.WARN)
                    TextAction("Approve all updates", { vm.includeAllUpdates() }, modifier = Modifier.testTag("approve_updates"))
                }
            }
            itemsIndexed(cands, key = { i, _ -> i }) { i, cand -> CandidateCard(i, cand, vm) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg).navigationBarsPadding()) {
            Hairline()
            PrimaryButton(if (busy) "Importing…" else if (selected == 0) "Nothing selected" else "Import $selected recipe${if (selected == 1) "" else "s"}",
                { vm.commit() }, Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m).testTag("confirm_import"),
                enabled = selected > 0 && !busy)
        }
    }
}

@Composable
private fun CandidateCard(index: Int, cand: ImportCandidate, vm: RecipeImportViewModel) {
    val c = Forma.colors
    val p = cand.parsed
    val r = p.recipe
    var expanded by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    FormaCard(Modifier.testTag("cand_$index"), padding = PaddingValues(start = Space.xs, end = Space.l, top = Space.s, bottom = Space.m)) {
        Row(verticalAlignment = Alignment.Top) {
            if (p.valid) CheckCircle(cand.include, { vm.setInclude(index, !cand.include) }, "Include ${p.name}", Modifier.testTag("include_$index"))
            else Spacer(Modifier.width(44.dp))
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(p.name, style = Forma.type.heading, color = c.text)
                Spacer(Modifier.height(Space.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Tag(cand.status.label, when (cand.status) {
                        ImportStatus.NEW -> Tone.ACCENT; ImportStatus.UPDATE -> Tone.WARN
                        ImportStatus.BUILT_IN_CONFLICT -> Tone.NEUTRAL; ImportStatus.INVALID -> Tone.NEGATIVE
                    })
                    if (r != null) {
                        KosherTag(KosherStatus.from(r.kosherStatus) ?: KosherStatus.PAREVE, r.isFish)
                        Tag(Category.from(r.category).label)
                        if (r.nutritionSource == "VERIFIED_LABELS") Tag("Verified labels", Tone.ACCENT)
                    }
                }
                if (r != null) {
                    val m = RecipeMath.macros(p.ingredients, Portion.STANDARD, 1.0, null)
                    Spacer(Modifier.height(Space.xs))
                    Text("${m.kcal.roundToInt()} kcal · ${m.proteinG.roundToInt()} g protein · ${p.ingredients.count { it.unit != TOTAL_UNIT }} ingredients" +
                        (if (r.tags.isNotEmpty()) " · ${r.tags.joinToString(", ")}" else ""), style = Forma.type.caption, color = c.textSecondary)
                }
                when (cand.status) {
                    ImportStatus.UPDATE -> Text(if (cand.include) "Will replace \"${cand.existing?.name}\" (favourite and photo kept)." else "Tick to replace \"${cand.existing?.name}\".",
                        style = Forma.type.caption, color = c.warn)
                    ImportStatus.BUILT_IN_CONFLICT -> Text("Built-in recipes aren't overwritten — this imports as a separate copy.", style = Forma.type.caption, color = c.textTertiary)
                    else -> {}
                }
            }
        }
        Column(Modifier.padding(start = 44.dp)) {
            cand.errors.forEach { Text("✕ ${it.path.substringAfter("]").removePrefix(".").ifEmpty { "recipe" }}: ${it.message}", style = Forma.type.caption, color = c.negative) }
            cand.warnings.forEach { Text("! ${it.message}", style = Forma.type.caption, color = c.warn) }
            if (cand.status == ImportStatus.INVALID) FixPanel(index, cand, vm)
            if (cand.infos.isNotEmpty()) {
                TextAction(if (showNotes) "Hide notes" else "${cand.infos.size} note${if (cand.infos.size == 1) "" else "s"}", { showNotes = !showNotes }, color = c.textTertiary)
                AnimatedVisibility(showNotes) { Column { cand.infos.forEach { Text("· ${it.message}", style = Forma.type.caption, color = c.textTertiary) } } }
            }
            if (r != null) {
                TextAction(if (expanded) "Hide preview" else "Preview recipe", { expanded = !expanded }, modifier = Modifier.testTag("preview_$index"))
                AnimatedVisibility(expanded) {
                    Column {
                        if (r.summary.isNotBlank()) Text(r.summary, style = Forma.type.bodySmall, color = c.textSecondary)
                        p.ingredients.filter { it.unit != TOTAL_UNIT }.forEach {
                            Text("• ${if (it.amount > 0) com.forma.app.util.Fmt.amount(it.amount) + " " + it.unit + " " else ""}${it.name}", style = Forma.type.bodySmall, color = c.text)
                        }
                        Spacer(Modifier.height(Space.xs))
                        (r.prepSteps + r.cookSteps).forEachIndexed { k, s -> Text("${k + 1}. $s", style = Forma.type.bodySmall, color = c.textSecondary) }
                        if (r.kosherNotes.isNotEmpty()) Text("Kosher: ${r.kosherNotes.joinToString(" ")}", style = Forma.type.caption, color = c.textTertiary)
                    }
                }
            }
        }
    }
}

/** Inline fixes for the fields people most often leave out (especially from plain-text recipes). */
@Composable
private fun FixPanel(index: Int, cand: ImportCandidate, vm: RecipeImportViewModel) {
    val c = Forma.colors
    val paths = cand.errors.map { it.path }
    val needCat = paths.any { it.endsWith(".category") }
    val needKosher = paths.any { it.endsWith(".kosher_type") } && cand.errors.none { it.message.contains("combined") || it.message.contains("Use Meat or Dairy") }
    val needKcal = paths.any { it.contains("nutrition_per_serving") }
    if (!needCat && !needKosher && !needKcal) return
    var cat by remember { mutableStateOf<String?>(null) }
    var kos by remember { mutableStateOf<String?>(null) }
    var kcal by remember { mutableStateOf("") }
    var prot by remember { mutableStateOf("") }
    Spacer(Modifier.height(Space.s))
    Text("Fix here", style = Forma.type.label, color = c.text)
    if (needCat) FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Category.entries.forEach { Chip(it.label, cat == it.label, { cat = it.label }, Modifier.testTag("fix_cat_${it.name}_$index")) }
    }
    if (needKosher) { Spacer(Modifier.height(Space.xs)); FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        listOf("Meat", "Dairy", "Pareve", "Fish").forEach { Chip(it, kos == it, { kos = it }, Modifier.testTag("fix_kosher_${it}_$index")) }
    } }
    if (needKcal) {
        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            FormaField(kcal, { kcal = it }, "kcal/serving", Modifier.weight(1f).testTag("fix_kcal_$index"), numeric = true)
            FormaField(prot, { prot = it }, "Protein g", Modifier.weight(1f).testTag("fix_protein_$index"), numeric = true)
        }
    }
    TextAction("Apply", { vm.fix(index, cat, kos, kcal.toDoubleOrNull(), prot.toDoubleOrNull()) }, modifier = Modifier.testTag("fix_apply_$index"))
}

@Composable
private fun ReportView(report: ImportReport, onDone: () -> Unit, onAnother: () -> Unit, context: Context) {
    val c = Forma.colors
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl).testTag("import_report")) {
        FormaCard {
            Row {
                StatBlock("Imported", "${report.count(ImportOutcome.IMPORTED)}", null, Modifier.weight(1f), valueColor = c.accent)
                StatBlock("Updated", "${report.count(ImportOutcome.UPDATED)}", null, Modifier.weight(1f))
                StatBlock("Skipped", "${report.count(ImportOutcome.SKIPPED)}", null, Modifier.weight(1f))
                StatBlock("Failed", "${report.count(ImportOutcome.FAILED)}", null, Modifier.weight(1f),
                    valueColor = if (report.count(ImportOutcome.FAILED) > 0) c.negative else c.text)
            }
        }
        if (report.count(ImportOutcome.IMPORTED) + report.count(ImportOutcome.UPDATED) > 0) {
            Spacer(Modifier.height(Space.s))
            Note("Imported recipes are in their collections, under My recipes, and in Add food.", Tone.ACCENT)
        }
        SectionHeader("Details")
        FormaCard {
            report.lines.forEachIndexed { i, l ->
                if (i > 0) Hairline()
                Column(Modifier.padding(vertical = Space.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name, style = Forma.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                        Tag(l.outcome.name.lowercase().replaceFirstChar { it.uppercase() }, when (l.outcome) {
                            ImportOutcome.IMPORTED, ImportOutcome.UPDATED -> Tone.ACCENT
                            ImportOutcome.SKIPPED -> Tone.NEUTRAL; ImportOutcome.FAILED -> Tone.NEGATIVE
                        })
                    }
                    l.details.forEach { Text(it, style = Forma.type.caption, color = if (l.outcome == ImportOutcome.FAILED) c.negative else c.textTertiary) }
                }
            }
        }
        Spacer(Modifier.height(Space.l))
        SecondaryButton(if (copied) "Copied" else "Copy report", {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Import report", report.asText())); copied = true
        }, Modifier.fillMaxWidth(), icon = Icons.Outlined.ContentCopy)
        Spacer(Modifier.height(Space.s))
        SecondaryButton("Import more", onAnother, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.s))
        PrimaryButton("Done", onDone, Modifier.fillMaxWidth().testTag("import_done"))
    }
}

