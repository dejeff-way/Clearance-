package com.forma.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.forma.app.BuildConfig
import com.forma.app.data.prefs.AppSettings
import com.forma.app.data.prefs.LengthUnit
import com.forma.app.data.prefs.ThemeMode
import com.forma.app.data.prefs.WeightUnit
import com.forma.app.data.repo.toEnergyProfile
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.popOnMain
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.ListRow
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.ScreenTitle
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.components.ValueRow
import com.forma.app.ui.nav.Routes
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.BudgetMode
import com.forma.engine.Metabolism
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(nav: NavController) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val profile by container.profile.profile.collectAsStateWithLifecycle(null)
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val meta by container.syncMeta.collectAsStateWithLifecycle(null)
    val model by container.energyModel.collectAsStateWithLifecycle()
    val c = Forma.colors
    var message by remember { mutableStateOf<String?>(null) }
    var resetDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { container.backup.exportTo(uri, container.time.nowMillis()) }
                .fold({ "Backup saved." }, { "Export failed: ${it.message}" })
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val m = model
        if (uri != null && m != null) scope.launch {
            message = runCatching { container.backup.writeText(uri, container.backup.historyCsv(m, 365)) }
                .fold({ "History exported." }, { "Export failed: ${it.message}" })
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    val p = profile ?: return Box(Modifier.fillMaxSize())
    val ep = p.toEnergyProfile()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = Space.gutter).padding(bottom = Space.xxl).testTag("settings"),
    ) {
        ScreenTitle("Settings")
        message?.let { Spacer(Modifier.height(Space.s)); Note(it, Tone.ACCENT) }

        SectionHeader("You")
        FormaCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            ListRow("Profile", "${if (p.sex == "FEMALE") "Female" else "Male"} · ${p.ageYears} · ${Fmt.heightFtIn(p.heightInches)} · started at ${Fmt.weight(p.startWeightLb, settings.weightUnit)} ${Fmt.weightUnit(settings.weightUnit)}",
                Icons.Outlined.Person, { nav.navigate(Routes.PROFILE) }, trailing = { Chevron() })
            Hairline()
            ListRow("Targets & budget",
                (if (p.budgetMode == BudgetMode.FIXED.name) "Fixed ${Fmt.kcal(p.fixedBudgetKcal ?: 0)} kcal" else "Dynamic · expenditure − ${p.desiredDeficitKcal}") +
                    " · protein ${p.proteinTargetG} g · min ${Fmt.kcal(p.minimumBudgetKcal)}",
                Icons.Outlined.Tune, { nav.navigate(Routes.TARGETS) }, trailing = { Chevron() })
            Hairline()
            ListRow("Calorie carryover", "Up to ${p.maxRepaymentPerDay} kcal/day · balance ${Fmt.kcal(model?.carryover?.balanceAfterToday ?: 0)}",
                Icons.Outlined.SwapVert, { nav.navigate(Routes.CARRYOVER) }, trailing = { Chevron() })
            Hairline()
            ListRow("Health Connect", if (meta?.grantedPermissions.isNullOrBlank()) "Not connected" else "Connected" +
                (meta?.lastSuccessAt?.let { " · synced ${Fmt.ago(it, System.currentTimeMillis())}" } ?: ""),
                Icons.Outlined.Favorite, { nav.navigate(Routes.HEALTH) }, trailing = { Chevron() })
        }

        SectionHeader("Starting estimates")
        FormaCard {
            ValueRow("BMR (Mifflin–St Jeor)", "${Fmt.kcal(Metabolism.bmr(ep))} kcal")
            ValueRow("Maintenance estimate", "${Fmt.kcal(Metabolism.maintenanceEstimate(ep))} kcal", "BMR × ${p.maintenanceActivityFactor} — includes typical training. An estimate.")
            ValueRow("Starting budget", "${Fmt.kcal(Metabolism.initialBudget(ep))} kcal", "Maintenance − ${p.desiredDeficitKcal}. Day to day, FORMA uses your measured or estimated expenditure instead.")
        }

        SectionHeader("Units")
        Segmented(WeightUnit.entries, settings.weightUnit, { if (it == WeightUnit.LB) "Pounds" else "Kilograms" }, { scope.launch { container.settings.setWeightUnit(it) } }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.s))
        Segmented(LengthUnit.entries, settings.lengthUnit, { if (it == LengthUnit.IN) "Inches" else "Centimetres" }, { scope.launch { container.settings.setLengthUnit(it) } }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.xs))
        Text("Energy is always shown in kilocalories (kcal).", style = Forma.type.caption, color = c.textTertiary)

        SectionHeader("Appearance")
        Segmented(ThemeMode.entries, settings.theme, { it.label }, { scope.launch { container.settings.setTheme(it) } }, Modifier.fillMaxWidth().testTag("theme_toggle"))

        SectionHeader("Data")
        FormaCard(padding = PaddingValues(horizontal = Space.l, vertical = Space.xs)) {
            ListRow("Export backup", "Everything, as a JSON file you can restore", Icons.Outlined.FileDownload,
                { exportJson.launch("forma-backup-${LocalDate.now()}.json") })
            Hairline()
            ListRow("Restore from backup", "Replaces current data with a FORMA backup", Icons.Outlined.Restore, { importJson.launch(arrayOf("application/json", "*/*")) })
            Hairline()
            ListRow("Import & export recipes", "forma.recipe JSON files, pasted data or recipe text", Icons.AutoMirrored.Outlined.MenuBook,
                { nav.navigate(Routes.IMPORT) })
            Hairline()
            ListRow("Export daily history (CSV)", "Intake, expenditure, deficit and weight per day", Icons.Outlined.TableChart,
                { exportCsv.launch("forma-history-${LocalDate.now()}.csv") })
            Hairline()
            ListRow("Reset all data", "Deletes your logs, weights, workouts and settings", Icons.Outlined.DeleteForever, { resetDialog = true }, titleColor = c.negative)
        }
        Spacer(Modifier.height(Space.s))
        Text("Your data is stored only on this phone and included in Android's device backup.", style = Forma.type.caption, color = c.textTertiary)

        SectionHeader("About")
        Text("FORMA ${BuildConfig.VERSION_NAME}", style = Forma.type.bodyStrong, color = c.text)
        Spacer(Modifier.height(Space.xs))
        Text("Calorie, expenditure and nutrition figures are estimates — not exact measurements and not medical advice. " +
            "Recipe nutrition should be verified against the exact products you buy. Kosher notes are general guidance; follow your own rabbi and community practice.",
            style = Forma.type.caption, color = c.textTertiary)
    }

    restoreUri?.let { uri ->
        com.forma.app.ui.components.ConfirmDialog("Restore this backup?", "All current logs, weights, workouts and settings will be replaced.", "Restore",
            {
                restoreUri = null
                scope.launch { message = runCatching { container.backup.importFrom(uri) }.fold({ "Backup restored." }, { "Restore failed: ${it.message}" }) }
            }, { restoreUri = null }, destructive = true)
    }
    if (resetDialog) ResetDialog({ resetDialog = false }) {
        resetDialog = false
        scope.launch {
            container.backup.resetAll()
            container.settings.clear()
            runCatching { container.healthSource.revokeAll() }
            container.initialize()
            message = "All data was reset."
        }
    }
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, null, tint = Forma.colors.textTertiary, modifier = Modifier.padding(start = Space.s).height(14.dp))

@Composable
private fun ResetDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val c = Forma.colors
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface,
        title = { Text("Reset all data?", style = Forma.type.heading, color = c.text) },
        text = {
            Column {
                Text("This permanently deletes your food log, weights, measurements, workouts, carryover and settings. Recipes are kept. Type RESET to confirm.",
                    style = Forma.type.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(Space.m))
                FormaField(text, { text = it }, "Type RESET", modifier = Modifier.testTag("reset_input"))
            }
        },
        confirmButton = { TextAction("Reset", { if (text.trim() == "RESET") onConfirm() }, color = if (text.trim() == "RESET") c.negative else c.textTertiary, modifier = Modifier.testTag("reset_confirm")) },
        dismissButton = { TextAction("Cancel", onDismiss, color = c.textSecondary) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(nav: NavController) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val profile by container.profile.profile.collectAsStateWithLifecycle(null)
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val p = profile ?: return Box(Modifier.fillMaxSize())
    val c = Forma.colors
    val wu = settings.weightUnit
    var name by remember(p.id) { mutableStateOf(p.displayName) }
    var sex by remember(p.id) { mutableStateOf(p.sex) }
    var age by remember(p.id) { mutableStateOf(p.ageYears.toString()) }
    var ft by remember(p.id) { mutableStateOf((p.heightInches.roundToInt() / 12).toString()) }
    var inch by remember(p.id) { mutableStateOf((p.heightInches.roundToInt() % 12).toString()) }
    var start by remember(p.id) { mutableStateOf(Fmt.weight(p.startWeightLb, wu)) }
    var current by remember(p.id) { mutableStateOf(Fmt.weight(p.currentWeightLb, wu)) }
    var goalW by remember(p.id) { mutableStateOf(p.goalWeightLb?.let { Fmt.weight(it, wu) } ?: "") }
    var main by remember(p.id) { mutableStateOf(p.mainGoal) }
    var secondary by remember(p.id) { mutableStateOf(p.secondaryGoal) }
    var ski by remember(p.id) { mutableStateOf(LocalDate.ofEpochDay(p.skiDateEpochDay)) }
    var gym by remember(p.id) { mutableStateOf(p.gym) }
    var days by remember(p.id) { mutableStateOf(p.trainingDays.split(",").filter { it.isNotBlank() }.toSet()) }
    var minutes by remember(p.id) { mutableStateOf(p.workoutMinutes.toString()) }
    var pickDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar("Profile", { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            FormaField(name, { name = it }, "First name (optional)")
            SectionHeader("Body")
            Segmented(listOf("MALE", "FEMALE"), sex, { if (it == "MALE") "Male" else "Female" }, { sex = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.m))
            FormaField(age, { age = it }, "Age", suffix = "years", numeric = true, decimal = false, modifier = Modifier.testTag("age_input"))
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FormaField(ft, { ft = it }, "Height", Modifier.weight(1f), suffix = "ft", numeric = true, decimal = false)
                FormaField(inch, { inch = it }, "", Modifier.weight(1f), suffix = "in", numeric = true, decimal = false)
            }
            Spacer(Modifier.height(Space.s))
            FormaField(start, { start = it }, "Starting weight", suffix = Fmt.weightUnit(wu), numeric = true)
            Spacer(Modifier.height(Space.s))
            FormaField(current, { current = it }, "Current weight", suffix = Fmt.weightUnit(wu), numeric = true,
                supporting = "Changing this logs a weigh-in for today.")
            Spacer(Modifier.height(Space.s))
            FormaField(goalW, { goalW = it }, "Goal weight (optional)", suffix = Fmt.weightUnit(wu), numeric = true)

            SectionHeader("Goals")
            FormaField(main, { main = it }, "Main goal", singleLine = false)
            Spacer(Modifier.height(Space.s))
            FormaField(secondary, { secondary = it }, "Secondary goal", singleLine = false)
            Spacer(Modifier.height(Space.s))
            ListRow("Ski-ready date", Fmt.fullDate(ski), onClick = { pickDate = true }, trailing = { TextAction("Change", { pickDate = true }) })

            SectionHeader("Training")
            FormaField(gym, { gym = it }, "Gym")
            Spacer(Modifier.height(Space.m))
            Text("Available training days", style = Forma.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).forEach { d ->
                    Chip(d.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, d.name in days, {
                        days = if (d.name in days) days - d.name else days + d.name
                    })
                }
            }
            Spacer(Modifier.height(Space.m))
            FormaField(minutes, { minutes = it }, "Typical workout length", suffix = "min", numeric = true, decimal = false)
            error?.let { Spacer(Modifier.height(Space.s)); Note(it, Tone.NEGATIVE) }
            Spacer(Modifier.height(Space.xl))
            PrimaryButton("Save profile", {
                val a = age.toIntOrNull(); val f = ft.toIntOrNull(); val i = inch.toIntOrNull() ?: 0
                val st = start.toDoubleOrNull(); val cur = current.toDoubleOrNull()
                when {
                    a == null || a !in 13..100 -> error = "Enter an age between 13 and 100"
                    f == null || f !in 3..8 || i !in 0..11 -> error = "Enter a valid height"
                    st == null || cur == null -> error = "Enter starting and current weight"
                    else -> scope.launch {
                        val curLb = Fmt.toLb(cur, wu)
                        container.profile.update {
                            it.copy(
                                displayName = name.trim(), sex = sex, ageYears = a, heightInches = (f * 12 + i).toDouble(),
                                startWeightLb = Fmt.toLb(st, wu), goalWeightLb = goalW.toDoubleOrNull()?.let { g -> Fmt.toLb(g, wu) },
                                mainGoal = main, secondaryGoal = secondary, skiDateEpochDay = ski.toEpochDay(), gym = gym,
                                trainingDays = days.joinToString(","), workoutMinutes = minutes.toIntOrNull() ?: it.workoutMinutes,
                            )
                        }
                        if (kotlin.math.abs(curLb - p.currentWeightLb) > 0.05) runCatching { container.body.logWeight(curLb) }
                        nav.popOnMain()
                    }
                }
            }, Modifier.fillMaxWidth().testTag("save_profile"))
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = ski.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextAction("OK", {
                state.selectedDateMillis?.let { ski = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                pickDate = false
            })
        }, dismissButton = { TextAction("Cancel", { pickDate = false }, color = c.textSecondary) }) { DatePicker(state) }
    }
}

@Composable
fun TargetsEditScreen(nav: NavController) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val profile by container.profile.profile.collectAsStateWithLifecycle(null)
    val model by container.energyModel.collectAsStateWithLifecycle()
    val p = profile ?: return Box(Modifier.fillMaxSize())
    val c = Forma.colors
    var mode by remember(p.id) { mutableStateOf(if (p.budgetMode == BudgetMode.FIXED.name) BudgetMode.FIXED else BudgetMode.DYNAMIC) }
    var fixed by remember(p.id) { mutableStateOf((p.fixedBudgetKcal ?: Metabolism.initialBudget(p.toEnergyProfile())).toString()) }
    var deficit by remember(p.id) { mutableStateOf(p.desiredDeficitKcal.toString()) }
    var protein by remember(p.id) { mutableStateOf(p.proteinTargetG.toString()) }
    var minimum by remember(p.id) { mutableStateOf(p.minimumBudgetKcal.toString()) }
    var repay by remember(p.id) { mutableStateOf(p.maxRepaymentPerDay.toString()) }
    var baseline by remember(p.id) { mutableStateOf(p.baselineActivityFactor) }
    var maint by remember(p.id) { mutableStateOf(p.maintenanceActivityFactor) }
    var stepGoal by remember(p.id) { mutableStateOf(p.stepGoal.toString()) }
    var adjPct by remember(p.id) { mutableStateOf(p.wearableAdjustmentPct) }
    var bonusCap by remember(p.id) { mutableStateOf(p.maxActivityBonusKcal) }
    var error by remember { mutableStateOf<String?>(null) }
    val dVal = deficit.toIntOrNull()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BackBar("Targets & budget", { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Text("Calorie target", style = Forma.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Space.s))
            Segmented(BudgetMode.entries, mode, { if (it == BudgetMode.DYNAMIC) "Dynamic" else "Fixed" }, { mode = it }, Modifier.fillMaxWidth().testTag("budget_mode"))
            Spacer(Modifier.height(Space.s))
            Text(
                if (mode == BudgetMode.DYNAMIC) "Budget = today's estimated expenditure − your deficit. It rises on active days."
                else "Budget stays at a number you choose. Carryover and the minimum still apply.",
                style = Forma.type.caption, color = c.textTertiary,
            )
            Spacer(Modifier.height(Space.m))
            if (mode == BudgetMode.FIXED) {
                FormaField(fixed, { fixed = it }, "Daily calorie target", suffix = "kcal", numeric = true, decimal = false, modifier = Modifier.testTag("fixed_input"))
                Spacer(Modifier.height(Space.s))
            }
            FormaField(deficit, { deficit = it }, "Desired daily deficit", suffix = "kcal", numeric = true, decimal = false,
                modifier = Modifier.testTag("deficit_input"), supporting = "≈500 kcal/day is a sustainable default (~1 lb/week).")
            if (dVal != null && dVal > 1000) Note("A deficit above 1,000 kcal/day is aggressive and makes it harder to keep muscle.", Tone.WARN)
            Spacer(Modifier.height(Space.s))
            FormaField(protein, { protein = it }, "Protein target", suffix = "g/day", numeric = true, decimal = false, modifier = Modifier.testTag("protein_input"))

            SectionHeader("Safeguards")
            FormaField(minimum, { minimum = it }, "Minimum calorie budget", suffix = "kcal", numeric = true, decimal = false,
                supporting = "FORMA warns and holds the budget here rather than going lower.", modifier = Modifier.testTag("minimum_input"))
            Spacer(Modifier.height(Space.s))
            FormaField(repay, { repay = it }, "Max carryover repayment", suffix = "kcal/day", numeric = true, decimal = false, modifier = Modifier.testTag("repay_input"))

            SectionHeader("Activity")
            FormaField(stepGoal, { stepGoal = it }, "Daily step goal", suffix = "steps", numeric = true, decimal = false, modifier = Modifier.testTag("step_goal_input"))
            Spacer(Modifier.height(Space.m))
            Text("Wearable calorie adjustment", style = Forma.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(0, -5, -10, -15).forEach { v -> Chip(if (v == 0) "None" else "$v%", adjPct == v, { adjPct = v }, Modifier.testTag("adj_$v")) }
            }
            Text("Wearables often overestimate activity calories. This trims only the activity part (never resting energy) before it counts.",
                style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = Space.xs))
            if (mode == BudgetMode.DYNAMIC) {
                Spacer(Modifier.height(Space.m))
                Text("Most activity can add to your food budget", style = Forma.type.caption, color = c.textSecondary)
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    listOf(300, 500, 800, 0).forEach { v -> Chip(if (v == 0) "No limit" else "+$v", bonusCap == v, { bonusCap = v }, Modifier.testTag("cap_$v")) }
                }
                Text("Active days still raise your budget, but never by more than this. Your actual deficit always uses the full estimate.",
                    style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = Space.xs))
            }

            SectionHeader("Estimation (advanced)")
            Text("Baseline activity factor — used only when Health Connect has no expenditure for a day.", style = Forma.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(1.2, 1.3, 1.4).forEach { f -> Chip("× ${"%.1f".format(f)}", baseline == f, { baseline = f }) }
            }
            Text("1.2 = sedentary outside of logged exercise (recommended, conservative).", style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = Space.xs))
            Spacer(Modifier.height(Space.m))
            Text("Initial maintenance factor — for the starting estimate shown in Settings.", style = Forma.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(1.375, 1.45, 1.55).forEach { f -> Chip("× $f", maint == f, { maint = f }) }
            }
            model?.let { m ->
                val preview = p.copy(baselineActivityFactor = baseline, desiredDeficitKcal = dVal ?: p.desiredDeficitKcal,
                    wearableAdjustmentPct = adjPct, maxActivityBonusKcal = bonusCap,
                    minimumBudgetKcal = minimum.toIntOrNull() ?: p.minimumBudgetKcal, budgetMode = mode.name,
                    fixedBudgetKcal = fixed.toIntOrNull()).toEnergyProfile(m.weightOn(m.s.today))
                val exp = com.forma.engine.ExpenditureEstimator.estimate(preview, m.inputs(m.s.today))
                val b = com.forma.engine.BudgetCalculator.compute(preview, exp.projectedDayKcal, m.carryover.scheduledToday)
                Spacer(Modifier.height(Space.l))
                FormaCard {
                    Text("Preview for today", style = Forma.type.caption, color = c.textSecondary)
                    ValueRow("Projected expenditure", "${Fmt.kcal(exp.projectedDayKcal)} kcal")
                    ValueRow("Budget", "${Fmt.kcal(b.finalBudget)} kcal", valueColor = c.accent)
                    b.warnings.forEach { Text(it, style = Forma.type.caption, color = c.warn) }
                }
            }
            error?.let { Spacer(Modifier.height(Space.s)); Note(it, Tone.NEGATIVE) }
            Spacer(Modifier.height(Space.xl))
            PrimaryButton("Save targets", {
                val d = deficit.toIntOrNull(); val pr = protein.toIntOrNull(); val mn = minimum.toIntOrNull(); val r = repay.toIntOrNull(); val fx = fixed.toIntOrNull()
                when {
                    d == null || d !in 0..1500 -> error = "Deficit must be between 0 and 1,500 kcal"
                    pr == null || pr !in 40..400 -> error = "Protein must be between 40 and 400 g"
                    mn == null || mn !in 1200..5000 -> error = "Minimum budget must be between 1,200 and 5,000 kcal"
                    r == null || r !in 0..500 -> error = "Repayment must be between 0 and 500 kcal/day"
                    stepGoal.toIntOrNull() == null || stepGoal.toInt() !in 1000..50000 -> error = "Step goal must be between 1,000 and 50,000"
                    mode == BudgetMode.FIXED && (fx == null || fx !in 1200..6000) -> error = "Fixed target must be between 1,200 and 6,000 kcal"
                    else -> scope.launch {
                        container.profile.update {
                            it.copy(budgetMode = mode.name, fixedBudgetKcal = if (mode == BudgetMode.FIXED) fx else it.fixedBudgetKcal,
                                desiredDeficitKcal = d, proteinTargetG = pr, minimumBudgetKcal = mn, maxRepaymentPerDay = r,
                                baselineActivityFactor = baseline, maintenanceActivityFactor = maint,
                                stepGoal = stepGoal.toInt(), wearableAdjustmentPct = adjPct, maxActivityBonusKcal = bonusCap)
                        }
                        nav.popOnMain()
                    }
                }
            }, Modifier.fillMaxWidth().testTag("save_targets"))
        }
    }
}
