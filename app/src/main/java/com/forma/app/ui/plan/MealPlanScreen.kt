package com.forma.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.db.MealPlanEntity
import com.forma.app.data.seed.Category
import com.forma.app.data.seed.KosherStatus
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.ConfirmDialog
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.IconAction
import com.forma.app.ui.components.KosherTag
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.SwitchRow
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.nav.Routes
import com.forma.app.ui.recipes.RecipeCardData
import com.forma.app.ui.recipes.RecipesViewModel
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Radius
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.Portion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanViewModel(private val c: AppContainer) : ViewModel() {
    private val today = c.time.today()

    /** From Thursday on, default to planning next week. */
    val weekStart = MutableStateFlow(
        c.mealPlan.weekStart(today).let { if (today.dayOfWeek in setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) it.plusWeeks(1) else it },
    )
    val entries = weekStart.flatMapLatest { c.mealPlan.week(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val thisWeek: LocalDate get() = c.mealPlan.weekStart(today)
    val message = MutableStateFlow<String?>(null)

    fun shift(weeks: Long) { weekStart.value = weekStart.value.plusWeeks(weeks); message.value = null }
    fun add(day: LocalDate, meal: Category, recipeId: String, portion: Portion, servings: Double, prepDays: Int) = viewModelScope.launch {
        c.mealPlan.add(day, meal.name, recipeId, portion, servings, prepDays)
    }
    fun change(e: MealPlanEntity, p: Portion, servings: Double, batch: Boolean) = viewModelScope.launch { c.mealPlan.changePortion(e, p, servings, batch) }
    fun remove(e: MealPlanEntity, batch: Boolean) = viewModelScope.launch { c.mealPlan.remove(e, batch) }
    fun clear() = viewModelScope.launch { c.mealPlan.clearWeek(weekStart.value); message.value = "Week cleared." }
    fun copyPrevious() = viewModelScope.launch {
        val n = c.mealPlan.copyWeek(weekStart.value.minusWeeks(1), weekStart.value)
        message.value = if (n == 0) "Nothing planned the week before to copy." else "Copied $n meals from the week before."
    }
}

private fun dayName(d: LocalDate) = d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun MealPlanScreen(nav: NavController) {
    val vm = formaViewModel { MealPlanViewModel(it) }
    val rvm = formaViewModel { RecipesViewModel(it) }
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val recipes by rvm.recipes.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val container = com.forma.app.ui.LocalContainer.current
    val profile by container.profile.profile.collectAsStateWithLifecycle(null)
    val c = Forma.colors
    val byId = recipes.associateBy { it.recipe.id }
    var adding by remember { mutableStateOf<Pair<LocalDate, Category>?>(null) }
    var editing by remember { mutableStateOf<MealPlanEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val weekLabel = when (weekStart) { vm.thisWeek -> "This week"; vm.thisWeek.plusWeeks(1) -> "Next week"; vm.thisWeek.minusWeeks(1) -> "Last week"; else -> null }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("meal_plan")) {
        BackBar("Meal plan", { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous week", { vm.shift(-1) })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${Fmt.shortDate(weekStart)} – ${Fmt.shortDate(weekStart.plusDays(6))}", style = Forma.type.titleSmall, color = c.text,
                        modifier = Modifier.testTag("plan_week"))
                    weekLabel?.let { Text(it, style = Forma.type.caption, color = c.textTertiary) }
                }
                IconAction(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next week", { vm.shift(1) }, modifier = Modifier.testTag("plan_next"))
            }
            Spacer(Modifier.height(Space.m))

            val planned = entries.size
            val daysPlanned = entries.map { it.epochDay }.distinct().size
            FormaCard {
                Row {
                    StatBlock("Meals planned", "$planned", null, Modifier.weight(1f))
                    StatBlock("Days with a plan", "$daysPlanned/7", null, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.m))
                PrimaryButton("Shopping list", { nav.navigate(Routes.shopping(weekStart.toEpochDay())) }, Modifier.fillMaxWidth().testTag("open_shopping"),
                    icon = Icons.Outlined.ShoppingCart, enabled = planned > 0)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    TextAction("Copy last week", { vm.copyPrevious() }, modifier = Modifier.testTag("plan_copy"))
                    if (planned > 0) TextAction("Clear week", { confirmClear = true }, color = c.textSecondary)
                }
            }
            message?.let { Spacer(Modifier.height(Space.s)); Note(it) }
            if (planned == 0) {
                Spacer(Modifier.height(Space.s))
                Note("Tap + under any meal to plan it. Leave days or meals blank whenever you like. Nothing is logged until you choose to.")
            }

            // ── Meal prep schedule ──
            val batches = entries.filter { it.batchId != null }.groupBy { it.batchId!! }.values.sortedBy { b -> b.minOf { it.epochDay } }
            if (batches.isNotEmpty()) {
                SectionHeader("Meal prep")
                FormaCard {
                    batches.forEachIndexed { i, b ->
                        if (i > 0) Hairline()
                        val first = b.minBy { it.epochDay }; val last = b.maxBy { it.epochDay }
                        val cook = b.firstOrNull { it.isPrepDay } ?: first
                        Column(Modifier.padding(vertical = Space.s)) {
                            Text("Cook ${dayName(LocalDate.ofEpochDay(cook.epochDay))}: ${byId[first.recipeId]?.recipe?.name ?: first.recipeId}",
                                style = Forma.type.bodyStrong, color = c.text)
                            Text("${b.size} servings · ${Category.from(first.mealType).label.lowercase()} " +
                                "${dayName(LocalDate.ofEpochDay(first.epochDay)).take(3)}–${dayName(LocalDate.ofEpochDay(last.epochDay)).take(3)}",
                                style = Forma.type.caption, color = c.textTertiary)
                        }
                    }
                }
            }

            // ── Days ──
            (0..6).map { weekStart.plusDays(it.toLong()) }.forEach { d ->
                val dayEntries = entries.filter { it.epochDay == d.toEpochDay() }
                SectionHeader("${dayName(d)} · ${Fmt.shortDate(d)}")
                FormaCard(Modifier.testTag("plan_day_${d.dayOfWeek.name.take(3)}")) {
                    if (dayEntries.isNotEmpty()) {
                        val totals = dayEntries.mapNotNull { e -> byId[e.recipeId]?.let { r -> e to r } }
                        val kcal = totals.sumOf { (e, r) -> r.macros.kcal * e.servings * (if (e.portion == Portion.LARGE.name) largeRatio(r) else 1.0) }
                        val prot = totals.sumOf { (e, r) -> r.macros.proteinG * e.servings * (if (e.portion == Portion.LARGE.name) largeRatio(r) else 1.0) }
                        val target = profile?.proteinTargetG ?: 180
                        Text("${Fmt.kcal(kcal)} kcal · ${prot.roundToInt()} g protein planned" + if (prot < target * 0.9) " (target $target g)" else "",
                            style = Forma.type.caption, color = if (prot >= target * 0.9) c.accent else c.textSecondary)
                    }
                    Category.entries.forEach { meal ->
                        val here = dayEntries.filter { it.mealType == meal.name }
                        Row(Modifier.fillMaxWidth().padding(top = Space.s), verticalAlignment = Alignment.Top) {
                            Text(meal.label, style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.width(76.dp).padding(top = 6.dp))
                            Column(Modifier.weight(1f)) {
                                here.forEach { e ->
                                    val r = byId[e.recipeId]
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.s)).clickable { editing = e }.padding(vertical = 4.dp)
                                        .testTag("plan_entry_${e.id}"), verticalAlignment = Alignment.CenterVertically) {
                                        Text(r?.recipe?.name ?: "Removed recipe", style = Forma.type.body, color = if (r == null) c.textTertiary else c.text,
                                            modifier = Modifier.weight(1f))
                                        if (e.portion == Portion.LARGE.name) { Tag("Large"); Spacer(Modifier.width(4.dp)) }
                                        if (e.servings != 1.0) { Tag("×${Fmt.amount(e.servings)}"); Spacer(Modifier.width(4.dp)) }
                                        if (e.batchId != null) Tag(if (e.isPrepDay) "Cook" else "Prepped", if (e.isPrepDay) Tone.ACCENT else Tone.NEUTRAL)
                                    }
                                }
                                if (here.isEmpty()) Text("—", style = Forma.type.body, color = c.line, modifier = Modifier.padding(top = 4.dp))
                            }
                            IconAction(Icons.Outlined.Add, "Add ${meal.label.lowercase()} on ${dayName(d)}", { adding = d to meal }, tint = c.accent,
                                modifier = Modifier.testTag("plan_add_${d.dayOfWeek.name.take(3)}_${meal.name}"))
                        }
                    }
                }
            }
        }
    }

    adding?.let { (d, meal) ->
        AddPlanSheet(d, meal, recipes, weekStart.plusDays(6), onDismiss = { adding = null }) { id, portion, servings, prep ->
            vm.add(d, meal, id, portion, servings, prep); adding = null
        }
    }
    editing?.let { e ->
        EditPlanSheet(e, byId[e.recipeId], onDismiss = { editing = null },
            onOpen = { editing = null; nav.navigate(Routes.recipe(e.recipeId)) },
            onChange = { p, s, b -> vm.change(e, p, s, b); editing = null },
            onRemove = { b -> vm.remove(e, b); editing = null })
    }
    if (confirmClear) ConfirmDialog("Clear this week?", "All planned meals for ${Fmt.shortDate(weekStart)} – ${Fmt.shortDate(weekStart.plusDays(6))} are removed. Your food log isn't affected.",
        "Clear", { vm.clear(); confirmClear = false }, { confirmClear = false }, destructive = true)
}

/** Large/standard calorie ratio, from the recipe's portion labels' underlying ingredients (approximated by 1.35 when unknown). */
private fun largeRatio(r: RecipeCardData): Double = r.largeKcal?.let { if (r.macros.kcal > 0) it / r.macros.kcal else null } ?: 1.35

@Composable
private fun AddPlanSheet(
    day: LocalDate, meal: Category, recipes: List<RecipeCardData>, lastDay: LocalDate,
    onDismiss: () -> Unit, onAdd: (String, Portion, Double, Int) -> Unit,
) {
    val c = Forma.colors
    var category by remember { mutableStateOf(meal) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<RecipeCardData?>(null) }
    var portion by remember { mutableStateOf(Portion.STANDARD) }
    var servings by remember { mutableStateOf(1.0) }
    var prepDays by remember { mutableStateOf(1) }
    val maxDays = (lastDay.toEpochDay() - day.toEpochDay() + 1).toInt()
    FormaSheet(onDismiss, "${meal.label} · ${dayName(day)}") {
        val p = picked
        if (p == null) {
            FormaField(query, { query = it }, "Search recipes")
            Spacer(Modifier.height(Space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Category.entries.forEach { Chip(it.label, it == category && query.isBlank(), { category = it; query = "" }) }
            }
            Spacer(Modifier.height(Space.s))
            recipes.filter { if (query.isBlank()) it.recipe.category == category.name else it.recipe.name.contains(query, true) }
                .sortedWith(compareByDescending<RecipeCardData> { it.recipe.isFavorite }.thenBy { it.recipe.sortOrder })
                .forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).clickable { picked = r }.padding(vertical = Space.s).testTag("plan_pick_${r.recipe.id}"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.recipe.name, style = Forma.type.bodyStrong, color = c.text)
                            Text("${r.macros.kcal.roundToInt()} kcal · ${r.macros.proteinG.roundToInt()} g protein · ${r.recipe.prepMinutes + r.recipe.cookMinutes} min",
                                style = Forma.type.caption, color = c.textTertiary)
                        }
                        KosherTag(KosherStatus.from(r.recipe.kosherStatus) ?: KosherStatus.PAREVE, r.recipe.isFish)
                    }
                    Hairline()
                }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.recipe.name, style = Forma.type.heading, color = c.text, modifier = Modifier.weight(1f))
                TextAction("Change", { picked = null }, color = c.textSecondary)
            }
            Spacer(Modifier.height(Space.m))
            Segmented(Portion.entries, portion, { if (it == Portion.STANDARD) "Standard" else "Large" }, { portion = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Servings", style = Forma.type.caption, color = c.textSecondary)
                    Text(Fmt.amount(servings), style = Forma.type.numberLarge, color = c.text)
                }
                IconAction(Icons.Outlined.Remove, "Fewer servings", { if (servings > 0.5) servings -= 0.5 })
                IconAction(Icons.Outlined.Add, "More servings", { if (servings < 4) servings += 0.5 })
            }
            Spacer(Modifier.height(Space.l))
            Text("Meal prep", style = Forma.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(Space.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                (1..maxDays.coerceAtMost(7)).forEach { n ->
                    Chip(if (n == 1) "Just this day" else "Cook once · $n days", prepDays == n, { prepDays = n }, Modifier.testTag("plan_prep_$n"))
                }
            }
            if (prepDays > 1) Text(
                "Cook on ${dayName(day)} and eat it for ${meal.label.lowercase()} through ${dayName(day.plusDays((prepDays - 1).toLong()))}. " +
                    "The shopping list buys enough for all $prepDays servings.",
                style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.padding(top = Space.xs),
            )
            Spacer(Modifier.height(Space.l))
            PrimaryButton(if (prepDays > 1) "Add $prepDays days" else "Add to plan", { onAdd(p.recipe.id, portion, servings, prepDays) },
                Modifier.fillMaxWidth().testTag("plan_confirm"))
        }
    }
}

@Composable
private fun EditPlanSheet(
    e: MealPlanEntity, r: RecipeCardData?, onDismiss: () -> Unit, onOpen: () -> Unit,
    onChange: (Portion, Double, Boolean) -> Unit, onRemove: (Boolean) -> Unit,
) {
    val c = Forma.colors
    var portion by remember { mutableStateOf(Portion.valueOf(e.portion)) }
    var servings by remember { mutableStateOf(e.servings) }
    var wholeBatch by remember { mutableStateOf(e.batchId != null) }
    FormaSheet(onDismiss, r?.recipe?.name ?: "Planned meal",
        "${Category.from(e.mealType).label} · ${dayName(LocalDate.ofEpochDay(e.epochDay))}" + if (e.batchId != null) " · meal prep" else "") {
        Segmented(Portion.entries, portion, { if (it == Portion.STANDARD) "Standard" else "Large" }, { portion = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Servings", style = Forma.type.caption, color = c.textSecondary)
                Text(Fmt.amount(servings), style = Forma.type.numberLarge, color = c.text)
            }
            IconAction(Icons.Outlined.Remove, "Fewer servings", { if (servings > 0.5) servings -= 0.5 })
            IconAction(Icons.Outlined.Add, "More servings", { if (servings < 4) servings += 0.5 })
        }
        if (e.batchId != null) SwitchRow("Apply to the whole meal-prep batch", null, wholeBatch, { wholeBatch = it })
        Spacer(Modifier.height(Space.m))
        PrimaryButton("Save", { onChange(portion, servings, wholeBatch) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.s))
        if (r != null) SecondaryButton("View recipe", onOpen, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.s))
        SecondaryButton(if (e.batchId != null && wholeBatch) "Remove whole batch" else "Remove from this day", { onRemove(e.batchId != null && wholeBatch) },
            Modifier.fillMaxWidth().testTag("plan_remove"), color = c.negative)
    }
}

