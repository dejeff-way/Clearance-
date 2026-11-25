package com.forma.app.ui.food

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.db.FoodLogEntity
import com.forma.app.data.repo.RecipeFull
import com.forma.app.data.seed.Category
import com.forma.app.di.AppContainer
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.popOnMain
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.ConfirmDialog
import com.forma.app.ui.components.EmptyState
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.IconAction
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.Tone
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.nav.Routes
import com.forma.app.ui.recipes.AddRecipeSheet
import com.forma.app.ui.recipes.RecipeRow
import com.forma.app.ui.recipes.RecipesViewModel
import com.forma.app.ui.recipes.sortedByRecipeSort
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.Portion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class FoodLogViewModel(private val c: AppContainer, startDay: Long) : ViewModel() {
    val day = MutableStateFlow(startDay)
    val entries = day.flatMapLatest { c.food.day(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val model = c.energyModel

    fun shift(d: Long) { day.value += d }
    fun delete(id: Long) = viewModelScope.launch { c.food.delete(id) }
    fun changePortion(id: Long, p: Portion, servings: Double) = viewModelScope.launch { c.food.changePortion(id, p, servings) }
    fun update(e: FoodLogEntity) = viewModelScope.launch { c.food.update(e) }
}

@Composable
fun FoodLogScreen(nav: NavController, epochDay: Long) {
    val container = LocalContainer.current
    val vm = formaViewModel(key = "log") { FoodLogViewModel(it, epochDay) }
    val day by vm.day.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FoodLogEntity?>(null) }
    val c = Forma.colors
    val date = LocalDate.ofEpochDay(day)
    val today = model?.s?.today ?: LocalDate.now()
    val summary = model?.summary(date)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("food_log"),
        contentPadding = PaddingValues(bottom = Space.xxl),
    ) {
        item { BackBar("Food log", { nav.popBackStack() }) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous day", { vm.shift(-1) })
                Text(Fmt.relativeDay(date, today), style = Forma.type.titleSmall, color = c.text, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconAction(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next day", { if (date.isBefore(today)) vm.shift(1) },
                    tint = if (date.isBefore(today)) c.text else c.line)
            }
        }
        item {
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Spacer(Modifier.height(Space.m))
                FormaCard {
                    val kcal = entries.sumOf { it.kcal }
                    Row {
                        StatBlock("Calories", Fmt.kcal(kcal), "kcal", Modifier.weight(1f), style = Forma.type.numberLarge)
                        if (summary != null) StatBlock("Budget", Fmt.kcal(summary.budget.finalBudget), "kcal", Modifier.weight(1f), style = Forma.type.numberLarge)
                    }
                    Spacer(Modifier.height(Space.m))
                    if (summary != null) {
                        Spacer(Modifier.height(Space.m)); Hairline(); Spacer(Modifier.height(Space.s))
                        Text(
                            (if (summary.remainingKcal >= 0) "${Fmt.kcal(summary.remainingKcal)} kcal remaining" else "${Fmt.kcal(-summary.remainingKcal)} kcal over budget") +
                                " · est. deficit ${Fmt.signed(if (date == today) summary.actualDeficitSoFar else summary.projectedDeficit).removePrefix("+")}",
                            style = Forma.type.caption, color = c.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(Space.m))
                PrimaryButton("Add food", { nav.navigate(Routes.addFood(day)) }, Modifier.fillMaxWidth().testTag("log_add_food"), icon = Icons.Outlined.Add)
                if (summary != null && model != null) {
                    Spacer(Modifier.height(Space.m))
                    com.forma.app.ui.components.MacrosCard(model!!.macroTargets(date, summary), entries.count { it.carbsG == null || it.fatG == null })
                }
                SectionHeader("Chronological")
            }
        }
        if (entries.isEmpty()) item {
            EmptyState("Nothing logged", "Food you add for this day will appear here in the order you ate it.", Modifier.padding(horizontal = Space.gutter))
        }
        items(entries, key = { it.id }) { e ->
            val t = Instant.ofEpochMilli(e.eatenAt).atZone(container.time.zone())
            Row(
                Modifier.fillMaxWidth().clickable { editing = e }.padding(horizontal = Space.gutter, vertical = Space.m).testTag("entry_${e.id}"),
                verticalAlignment = Alignment.Top,
            ) {
                Text(Fmt.time(t), style = Forma.type.numberSmall, color = c.textTertiary, modifier = Modifier.width(72.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = Forma.type.body, color = c.text)
                    Text(
                        listOfNotNull(
                            Category.from(e.mealType).label,
                            e.portion?.let { if (it == Portion.LARGE.name) "Large" else "Standard" },
                            e.servings.takeIf { it != 1.0 }?.let { "× ${Fmt.amount(it)}" },
                            "${e.proteinG.roundToInt()} g protein",
                        ).joinToString(" · "),
                        style = Forma.type.caption, color = c.textTertiary,
                    )
                }
                Text(Fmt.kcal(e.kcal), style = Forma.type.number, color = c.text)
            }
            Hairline(Modifier.padding(horizontal = Space.gutter))
        }
        item {
            val m = model
            if (m != null) {
                val week = (6 downTo 0).map { date.minusDays(it.toLong()) }
                val logged = week.filter { m.hasFood(it) }
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    SectionHeader("7-day averages")
                    FormaCard {
                        if (logged.isEmpty()) Text("No food logged in the last 7 days.", style = Forma.type.bodySmall, color = c.textSecondary)
                        else Row {
                            StatBlock("Intake", Fmt.kcal(logged.map { m.consumed(it).kcal }.average()), "kcal", Modifier.weight(1f))
                            StatBlock("Protein", "${logged.map { m.consumed(it).proteinG }.average().roundToInt()}", "g", Modifier.weight(1f))
                            StatBlock("Days logged", "${logged.size}/7", null, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(Space.s))
                        Text("Averages include only days with food logged.", style = Forma.type.caption, color = c.textTertiary)
                    }
                }
            }
        }
    }

    editing?.let { e ->
        EditEntrySheet(e, onDismiss = { editing = null },
            onDelete = { vm.delete(e.id); editing = null },
            onPortion = { p, s -> vm.changePortion(e.id, p, s); editing = null },
            onSave = { vm.update(it); editing = null })
    }
}

@Composable
private fun EditEntrySheet(
    e: FoodLogEntity, onDismiss: () -> Unit, onDelete: () -> Unit, onPortion: (Portion, Double) -> Unit, onSave: (FoodLogEntity) -> Unit,
) {
    val c = Forma.colors
    var confirmDelete by remember { mutableStateOf(false) }
    var meal by remember { mutableStateOf(Category.from(e.mealType)) }
    FormaSheet(onDismiss, e.name, "${Fmt.kcal(e.kcal)} kcal · ${e.proteinG.roundToInt()} g protein") {
        Text("Meal", style = Forma.type.caption, color = c.textSecondary)
        Spacer(Modifier.height(Space.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Category.entries.forEach { Chip(it.label, it == meal, { meal = it }) }
        }
        Spacer(Modifier.height(Space.l))
        if (e.recipeId != null) {
            var portion by remember { mutableStateOf(Portion.entries.firstOrNull { it.name == e.portion } ?: Portion.STANDARD) }
            var servings by remember { mutableStateOf(e.servings) }
            Text("Portion", style = Forma.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Space.s))
            Segmented(Portion.entries, portion, { if (it == Portion.STANDARD) "Standard" else "Large" }, { portion = it }, Modifier.fillMaxWidth().testTag("edit_portion"))
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Servings", style = Forma.type.caption, color = c.textSecondary)
                    Text(Fmt.amount(servings), style = Forma.type.numberLarge, color = c.text)
                }
                IconAction(Icons.Outlined.Remove, "Fewer servings", { if (servings > 0.5) servings -= 0.5 })
                IconAction(Icons.Outlined.Add, "More servings", { if (servings < 6) servings += 0.5 })
            }
            Spacer(Modifier.height(Space.l))
            PrimaryButton("Save changes", {
                if (meal.name != e.mealType) onSave(e.copy(mealType = meal.name))
                onPortion(portion, servings)
            }, Modifier.fillMaxWidth().testTag("save_entry"))
        } else {
            var name by remember { mutableStateOf(e.name) }
            var kcal by remember { mutableStateOf(e.kcal.roundToInt().toString()) }
            var protein by remember { mutableStateOf(if (e.proteinG > 0) e.proteinG.roundToInt().toString() else "") }
            var carbs by remember { mutableStateOf(e.carbsG?.roundToInt()?.toString() ?: "") }
            var fat by remember { mutableStateOf(e.fatG?.roundToInt()?.toString() ?: "") }
            FormaField(name, { name = it }, "Name")
            Spacer(Modifier.height(Space.s))
            FormaField(kcal, { kcal = it }, "Calories", suffix = "kcal", numeric = true, modifier = Modifier.testTag("edit_kcal"))
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FormaField(protein, { protein = it }, "Protein", Modifier.weight(1f), suffix = "g", numeric = true)
                FormaField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), suffix = "g", numeric = true)
                FormaField(fat, { fat = it }, "Fat", Modifier.weight(1f), suffix = "g", numeric = true)
            }
            Spacer(Modifier.height(Space.l))
            PrimaryButton("Save changes", {
                val k = kcal.toDoubleOrNull() ?: return@PrimaryButton
                onSave(e.copy(name = name.ifBlank { e.name }, kcal = k, proteinG = protein.toDoubleOrNull() ?: 0.0,
                    carbsG = carbs.toDoubleOrNull(), fatG = fat.toDoubleOrNull(), mealType = meal.name))
            }, Modifier.fillMaxWidth().testTag("save_entry"), enabled = kcal.toDoubleOrNull() != null)
        }
        Spacer(Modifier.height(Space.s))
        SecondaryButton("Delete entry", { confirmDelete = true }, Modifier.fillMaxWidth().testTag("delete_entry"), color = c.negative)
    }
    if (confirmDelete) ConfirmDialog("Delete this entry?", "${e.name} will be removed from the log and your totals.", "Delete",
        onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false }, destructive = true)
}

private enum class AddTab { LIBRARY, MANUAL }

@Composable
fun AddFoodScreen(nav: NavController, epochDay: Long, mealArg: String?) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val rvm = formaViewModel { RecipesViewModel(it) }
    val recipes by rvm.recipes.collectAsStateWithLifecycle()
    val log by container.food.day(epochDay).collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(AddTab.LIBRARY) }
    var query by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf<RecipeFull?>(null) }
    var meal by rememberSaveable { mutableStateOf(Category.from(mealArg ?: Category.SNACKS.name)) }
    val c = Forma.colors
    val date = LocalDate.ofEpochDay(epochDay)
    val today = container.time.today()

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("add_food_screen")) {
        BackBar("Add food · ${Fmt.relativeDay(date, today)}", { nav.popBackStack() })
        Segmented(AddTab.entries, tab, { if (it == AddTab.LIBRARY) "Recipe library" else "Manual entry" }, { tab = it },
            Modifier.fillMaxWidth().padding(horizontal = Space.gutter).testTag("add_tabs"))
        Spacer(Modifier.height(Space.m))
        when (tab) {
            AddTab.LIBRARY -> {
                val sortPref by container.settings.recipeSort.collectAsStateWithLifecycle(null)
                val sort = com.forma.app.ui.recipes.RecipeSort.decode(sortPref)
                val filtered = recipes.filter { query.isBlank() || it.recipe.name.contains(query, true) }
                // Recommended: saved first, then this meal's collection. Any other sort applies to the whole list.
                val shown = if (sort.key == com.forma.app.ui.recipes.RecipeSortKey.RECOMMENDED)
                    filtered.sortedWith(compareByDescending<com.forma.app.ui.recipes.RecipeCardData> { it.recipe.isFavorite }
                        .thenBy { if (it.recipe.category == meal.name) 0 else 1 }.thenBy { it.recipe.sortOrder })
                else filtered.sortedByRecipeSort(sort)
                LazyColumn(contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xxl),
                    verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    item {
                        FormaField(query, { query = it }, "Search recipes")
                        Spacer(Modifier.height(Space.xs))
                        com.forma.app.ui.recipes.SortRow(sort, { scope.launch { container.settings.setRecipeSort(it.encode()) } })
                        if (sort.key == com.forma.app.ui.recipes.RecipeSortKey.RECOMMENDED)
                            Text("Saved recipes first, then ${meal.label.lowercase()} suggestions.", style = Forma.type.caption, color = c.textTertiary)
                    }
                    items(shown, key = { it.recipe.id }) { d ->
                        RecipeRow(d, onClick = {
                            scope.launch { picking = container.recipes.full(d.recipe.id) }
                        })
                    }
                }
            }
            AddTab.MANUAL -> ManualEntry(meal, { meal = it }) { name, kcal, p, cb, f ->
                scope.launch {
                    val at = if (date == today) container.time.nowMillis() else container.time.startOfDayMillis(date) + 12 * 3_600_000L
                    container.food.logManual(name, meal.name, kcal, p, cb, f, epochDay, at)
                    nav.popOnMain()
                }
            }
        }
    }
    picking?.let { f ->
        AddRecipeSheet(f, Portion.STANDARD, today, if (date == today) log else emptyList(), onDismiss = { picking = null }) { m, p, s, d ->
            scope.launch {
                val at = if (d == today) container.time.nowMillis() else container.time.startOfDayMillis(d) + 12 * 3_600_000L
                container.food.logRecipe(f.recipe.id, m.name, p, s, d.toEpochDay(), at)
                picking = null
                nav.popOnMain()
            }
        }
    }
}

@Composable
private fun ManualEntry(meal: Category, onMeal: (Category) -> Unit, onAdd: (String, Double, Double?, Double?, Double?) -> Unit) {
    val c = Forma.colors
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    val k = kcal.toDoubleOrNull()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
        Text("Meal", style = Forma.type.caption, color = c.textSecondary)
        Spacer(Modifier.height(Space.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Category.entries.forEach { Chip(it.label, it == meal, { onMeal(it) }) }
        }
        Spacer(Modifier.height(Space.l))
        FormaField(name, { name = it }, "What did you eat?", modifier = Modifier.testTag("manual_name"))
        Spacer(Modifier.height(Space.s))
        FormaField(kcal, { kcal = it }, "Calories", suffix = "kcal", numeric = true, modifier = Modifier.testTag("manual_kcal"),
            supporting = "Required. Everything else is optional.")
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            FormaField(protein, { protein = it }, "Protein", Modifier.weight(1f).testTag("manual_protein"), suffix = "g", numeric = true)
            FormaField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), suffix = "g", numeric = true)
            FormaField(fat, { fat = it }, "Fat", Modifier.weight(1f), suffix = "g", numeric = true)
        }
        Spacer(Modifier.height(Space.l))
        PrimaryButton(if (k != null) "Add ${k.roundToInt()} kcal" else "Add", {
            if (k != null) onAdd(name.ifBlank { "Quick add" }, k, protein.toDoubleOrNull(), carbs.toDoubleOrNull(), fat.toDoubleOrNull())
        }, Modifier.fillMaxWidth().testTag("manual_add"), enabled = k != null && k >= 0)
        Spacer(Modifier.height(Space.m))
        Note("Not sure of the exact number? A rough estimate is better than leaving it out.", Tone.NEUTRAL)
    }
}
