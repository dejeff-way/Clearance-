package com.forma.app.ui.recipes

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import com.forma.app.data.db.IngredientEntity
import com.forma.app.data.db.RecipeEntity
import com.forma.app.data.repo.RecipeMath
import com.forma.app.data.repo.TOTAL_UNIT
import com.forma.app.data.seed.Category
import com.forma.app.data.seed.KosherStatus
import com.forma.app.ui.LocalContainer
import com.forma.app.ui.components.BackBar
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.IconAction
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SecondaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.SwitchRow
import com.forma.app.ui.components.Tone
import com.forma.app.ui.nav.Routes
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.Portion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

/** Editable ingredient row. Strings keep the user's typing intact. */
private class Draft(
    name: String = "", amount: String = "", unit: String = "g",
    kcal: String = "", protein: String = "", carbs: String = "", fat: String = "",
    scales: Boolean = true, optional: Boolean = false,
) {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
    var unit by mutableStateOf(unit)
    var kcal by mutableStateOf(kcal)
    var protein by mutableStateOf(protein)
    var carbs by mutableStateOf(carbs)
    var fat by mutableStateOf(fat)
    var scales by mutableStateOf(scales)
    var optional by mutableStateOf(optional)
}

private enum class NutritionMode { PER_INGREDIENT, TOTAL }

private val units = listOf("g", "ml", "cup", "tbsp", "tsp", "whole", "oz", "slice", "scoop")
private val dairyWords = listOf("milk", "yogurt", "yoghurt", "cheese", "butter", "cream", "whey", "parmesan", "mozzarella", "tnuva")
private val meatWords = listOf("chicken", "beef", "turkey", "lamb", "veal", "meat", "pastrami", "salami", "schnitzel", "brisket")

private fun num(s: String) = s.trim().replace(',', '.').toDoubleOrNull()
private fun numText(d: Double) = if (d == 0.0) "" else if (d % 1.0 == 0.0) d.toInt().toString() else Fmt.one(d).trimEnd('0').trimEnd('.')

/**
 * Create or edit your own recipe. [editId] edits an existing custom recipe; [copyFrom] starts from any recipe
 * (built-in recipes are copied, never changed).
 */
@Composable
fun RecipeEditorScreen(nav: NavController, editId: String?, copyFrom: String?) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val c = Forma.colors

    var loaded by remember { mutableStateOf(editId == null && copyFrom == null) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.LUNCH) }
    var kosher by remember { mutableStateOf(KosherStatus.PAREVE) }
    var fish by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    var prepMin by remember { mutableStateOf("") }
    var cookMin by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(NutritionMode.PER_INGREDIENT) }
    var multiplier by remember { mutableStateOf(1.5) }
    var totalKcal by remember { mutableStateOf("") }
    var totalP by remember { mutableStateOf("") }
    var totalC by remember { mutableStateOf("") }
    var totalF by remember { mutableStateOf("") }
    var prep by remember { mutableStateOf("") }
    var cook by remember { mutableStateOf("") }
    var kosherNotes by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }
    val ingredients = remember { mutableStateListOf(Draft()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(editId, copyFrom) {
        val src = (editId ?: copyFrom)?.let { container.recipes.full(it) } ?: run { loaded = true; return@LaunchedEffect }
        val r = src.recipe
        name = if (copyFrom != null) "${r.name} (my version)" else r.name
        category = Category.from(r.category)
        kosher = KosherStatus.from(r.kosherStatus) ?: KosherStatus.PAREVE
        fish = r.isFish
        summary = r.summary
        prepMin = r.prepMinutes.takeIf { it > 0 }?.toString() ?: ""
        cookMin = r.cookMinutes.takeIf { it > 0 }?.toString() ?: ""
        prep = r.prepSteps.joinToString("\n")
        cook = r.cookSteps.joinToString("\n")
        kosherNotes = r.kosherNotes.joinToString("\n")
        notes = r.tips.filterNot { it.startsWith("Nutrition is an estimate") }.joinToString("\n")
        favorite = r.isFavorite && editId != null
        val total = src.ingredients.firstOrNull { it.unit == TOTAL_UNIT }
        mode = if (total != null) NutritionMode.TOTAL else NutritionMode.PER_INGREDIENT
        total?.let {
            totalKcal = numText(it.kcal); totalP = numText(it.proteinG); totalC = numText(it.carbsG); totalF = numText(it.fatG)
            if (it.amount > 0) multiplier = it.largeAmount / it.amount
        }
        val scaled = src.ingredients.filter { it.unit != TOTAL_UNIT && it.amount > 0 && it.largeAmount != it.amount }
        if (total == null && scaled.isNotEmpty()) {
            val m = scaled.map { it.largeAmount / it.amount }.average()
            multiplier = listOf(1.25, 1.5, 2.0).minBy { kotlin.math.abs(it - m) }
        }
        ingredients.clear()
        src.ingredients.filter { it.unit != TOTAL_UNIT }.forEach { i ->
            // Milk-choice ingredients become plain ingredients using the recipe's current milk.
            val m = RecipeMath.ingredientMacros(i, Portion.STANDARD, src.milk)
            ingredients += Draft(
                name = if (i.isMilk) src.milk?.label ?: i.name else i.name,
                amount = numText(i.amount), unit = if (i.unit == "to taste") "to taste" else i.unit,
                kcal = numText(m.kcal), protein = numText(m.proteinG), carbs = m.carbsG?.let(::numText) ?: "", fat = m.fatG?.let(::numText) ?: "",
                scales = i.largeAmount != i.amount, optional = i.optional,
            )
        }
        if (ingredients.isEmpty()) ingredients += Draft()
        loaded = true
    }

    fun buildIngredients(recipeId: String): List<IngredientEntity> {
        val rows = ingredients.filter { it.name.isNotBlank() }.mapIndexed { idx, d ->
            val amt = num(d.amount) ?: 0.0
            val perIng = mode == NutritionMode.PER_INGREDIENT
            IngredientEntity(
                recipeId = recipeId, position = idx, name = d.name.trim(), amount = amt,
                unit = if (amt == 0.0) "to taste" else d.unit.ifBlank { "g" },
                largeAmount = if (d.scales && amt > 0) amt * multiplier else amt,
                kcal = if (perIng) num(d.kcal) ?: 0.0 else 0.0,
                proteinG = if (perIng) num(d.protein) ?: 0.0 else 0.0,
                carbsG = if (perIng) num(d.carbs) ?: 0.0 else 0.0,
                fatG = if (perIng) num(d.fat) ?: 0.0 else 0.0,
                optional = d.optional,
            )
        }
        return if (mode == NutritionMode.TOTAL) rows + IngredientEntity(
            recipeId, rows.size, "Nutrition per serving", 1.0, TOTAL_UNIT, multiplier, null,
            num(totalKcal) ?: 0.0, num(totalP) ?: 0.0, num(totalC) ?: 0.0, num(totalF) ?: 0.0,
        ) else rows
    }

    val previewStd = RecipeMath.macros(buildIngredients("preview"), Portion.STANDARD, 1.0, null)
    val previewLarge = RecipeMath.macros(buildIngredients("preview"), Portion.LARGE, 1.0, null)
    val allNames = ingredients.joinToString(" ") { it.name.lowercase() }
    val kosherWarning = when {
        kosher == KosherStatus.MEAT && dairyWords.any { allNames.contains(it) } ->
            "This is marked Meat but an ingredient looks like dairy. Meat and dairy must never be combined — check the ingredient or the designation."
        kosher == KosherStatus.DAIRY && meatWords.any { allNames.contains(it) } ->
            "This is marked Dairy but an ingredient looks like meat. Meat and dairy must never be combined — check the ingredient or the designation."
        kosher == KosherStatus.PAREVE && !fish && (dairyWords + meatWords).any { allNames.contains(it) } ->
            "This is marked Pareve but contains an ingredient that looks like meat or dairy. Check the designation."
        else -> null
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("recipe_editor")) {
        BackBar(if (editId != null) "Edit recipe" else "New recipe", { nav.popBackStack() })
        if (!loaded) return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.xxl)) {
            FormaField(name, { name = it }, "Recipe name", modifier = Modifier.testTag("edit_name"))
            Spacer(Modifier.height(Space.s))
            FormaField(summary, { summary = it }, "Short description (optional)", singleLine = false)

            SectionHeader("Collection")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Category.entries.forEach { Chip(it.label, it == category, { category = it }, Modifier.testTag("edit_cat_${it.name}")) }
            }

            SectionHeader("Kosher status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                KosherStatus.entries.forEach { k -> Chip(k.label, k == kosher, { kosher = k; if (k != KosherStatus.PAREVE) fish = false }, Modifier.testTag("edit_kosher_${k.name}")) }
            }
            if (kosher == KosherStatus.PAREVE) SwitchRow("Fish", "Pareve, but by custom not eaten together with meat.", fish, { fish = it })
            kosherWarning?.let { Spacer(Modifier.height(Space.s)); Note(it, Tone.WARN) }

            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FormaField(prepMin, { prepMin = it }, "Prep", Modifier.weight(1f), suffix = "min", numeric = true, decimal = false)
                FormaField(cookMin, { cookMin = it }, "Cook", Modifier.weight(1f), suffix = "min", numeric = true, decimal = false)
            }

            SectionHeader("Nutrition")
            Segmented(NutritionMode.entries, mode, { if (it == NutritionMode.PER_INGREDIENT) "Per ingredient" else "Total per serving" }, { mode = it },
                Modifier.fillMaxWidth().testTag("edit_mode"))
            Spacer(Modifier.height(Space.xs))
            Text(
                if (mode == NutritionMode.PER_INGREDIENT) "Enter calories for each ingredient's amount (from its label). FORMA adds them up and scales each one for a large portion."
                else "Know the totals already? Enter one standard serving's nutrition. Ingredients are then just a list.",
                style = Forma.type.caption, color = c.textTertiary,
            )
            if (mode == NutritionMode.TOTAL) {
                Spacer(Modifier.height(Space.m))
                FormaField(totalKcal, { totalKcal = it }, "Calories per serving", suffix = "kcal", numeric = true, modifier = Modifier.testTag("edit_total_kcal"))
                Spacer(Modifier.height(Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    FormaField(totalP, { totalP = it }, "Protein", Modifier.weight(1f).testTag("edit_total_protein"), suffix = "g", numeric = true)
                    FormaField(totalC, { totalC = it }, "Carbs", Modifier.weight(1f), suffix = "g", numeric = true)
                    FormaField(totalF, { totalF = it }, "Fat", Modifier.weight(1f), suffix = "g", numeric = true)
                }
            }

            SectionHeader("Ingredients")
            ingredients.forEachIndexed { idx, d ->
                FormaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FormaField(d.name, { d.name = it }, "Ingredient ${idx + 1}", Modifier.weight(1f).testTag("ing_name_$idx"))
                        IconAction(Icons.Outlined.DeleteOutline, "Remove ingredient", {
                            if (ingredients.size > 1) ingredients.removeAt(idx) else { ingredients[0] = Draft() }
                        }, tint = c.textTertiary)
                    }
                    Spacer(Modifier.height(Space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        FormaField(d.amount, { d.amount = it }, "Amount", Modifier.weight(1f).testTag("ing_amount_$idx"), numeric = true,
                            placeholder = "blank = to taste")
                        FormaField(d.unit, { d.unit = it }, "Unit", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Space.xs))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        units.forEach { u -> Chip(u, d.unit == u, { d.unit = u }) }
                    }
                    if (mode == NutritionMode.PER_INGREDIENT) {
                        Spacer(Modifier.height(Space.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            FormaField(d.kcal, { d.kcal = it }, "kcal", Modifier.weight(1f).testTag("ing_kcal_$idx"), numeric = true)
                            FormaField(d.protein, { d.protein = it }, "Protein g", Modifier.weight(1f).testTag("ing_protein_$idx"), numeric = true)
                        }
                        Spacer(Modifier.height(Space.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            FormaField(d.carbs, { d.carbs = it }, "Carbs g", Modifier.weight(1f), numeric = true)
                            FormaField(d.fat, { d.fat = it }, "Fat g", Modifier.weight(1f), numeric = true)
                        }
                    }
                    SwitchRow("More in a large portion", "Turn off for oil, spices and sauces that stay the same.", d.scales, { d.scales = it })
                    SwitchRow("Optional", "Listed but not counted in nutrition.", d.optional, { d.optional = it })
                }
                Spacer(Modifier.height(Space.s))
            }
            SecondaryButton("Add ingredient", { ingredients += Draft() }, Modifier.fillMaxWidth().testTag("add_ingredient"), icon = Icons.Outlined.Add)

            SectionHeader("Large portion")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(1.25, 1.5, 2.0).forEach { m -> Chip("${Fmt.amount(m)}×", multiplier == m, { multiplier = m }) }
            }
            Spacer(Modifier.height(Space.m))
            FormaCard {
                Row {
                    StatBlock("Standard", "${previewStd.kcal.roundToInt()}", "kcal · ${previewStd.proteinG.roundToInt()} g protein", Modifier.weight(1f))
                    StatBlock("Large", "${previewLarge.kcal.roundToInt()}", "kcal · ${previewLarge.proteinG.roundToInt()} g", Modifier.weight(1f))
                }
            }

            SectionHeader("Method")
            FormaField(prep, { prep = it }, "Preparation — one step per line", singleLine = false, modifier = Modifier.testTag("edit_prep"))
            Spacer(Modifier.height(Space.s))
            FormaField(cook, { cook = it }, "Cooking — one step per line", singleLine = false)
            Spacer(Modifier.height(Space.s))
            FormaField(kosherNotes, { kosherNotes = it }, "Kosher notes (optional) — one per line", singleLine = false)
            Spacer(Modifier.height(Space.s))
            FormaField(notes, { notes = it }, "Other notes (optional)", singleLine = false)

            error?.let { Spacer(Modifier.height(Space.m)); Note(it, Tone.NEGATIVE) }
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(if (saving) "Saving…" else "Save recipe", {
                val ings = buildIngredients("x")
                when {
                    name.isBlank() -> error = "Give the recipe a name."
                    ings.none { it.unit != TOTAL_UNIT } -> error = "Add at least one ingredient."
                    mode == NutritionMode.TOTAL && num(totalKcal) == null -> error = "Enter calories per serving."
                    mode == NutritionMode.PER_INGREDIENT && ingredients.any { it.name.isNotBlank() && it.kcal.isNotBlank() && num(it.kcal) == null } ->
                        error = "Check the calorie numbers."
                    else -> {
                        saving = true
                        scope.launch {
                            val id = editId ?: "custom_${UUID.randomUUID()}"
                            val lines = { s: String -> s.lines().map { it.trim().trimStart('•', '-', ' ').trim() }.filter { it.isNotBlank() } }
                            val entity = RecipeEntity(
                                id = id, name = name.trim(), category = category.name, kosherStatus = kosher.name, isFish = fish,
                                summary = summary.trim(), prepMinutes = prepMin.toIntOrNull() ?: 0, cookMinutes = cookMin.toIntOrNull() ?: 0,
                                prepSteps = lines(prep), cookSteps = lines(cook), kosherNotes = lines(kosherNotes), safetyNotes = emptyList(),
                                tips = lines(notes) + "Nutrition is an estimate from the values you entered.",
                                standardPortionLabel = "Standard portion", largePortionLabel = "Large portion (${Fmt.amount(multiplier)}× scaled ingredients)",
                                isFavorite = favorite, artKey = "custom_${category.name}", builtIn = false,
                            )
                            runCatching { container.recipes.saveCustom(entity, buildIngredients(id)) }
                                .onSuccess {
                                    withContext(Dispatchers.Main.immediate) {
                                        nav.popBackStack()
                                        if (editId == null) nav.navigate(Routes.recipe(id))
                                    }
                                }
                                .onFailure { error = it.message; saving = false }
                        }
                    }
                }
            }, Modifier.fillMaxWidth().testTag("save_recipe"), enabled = !saving)
        }
    }
}

