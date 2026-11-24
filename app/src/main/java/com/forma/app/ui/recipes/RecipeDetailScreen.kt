package com.forma.app.ui.recipes

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.db.FoodLogEntity
import com.forma.app.data.repo.RecipeFull
import com.forma.app.data.repo.RecipeMath
import com.forma.app.data.seed.Category
import com.forma.app.data.seed.KosherStatus
import com.forma.app.data.seed.MilkOption
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.Chip
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.IconAction
import com.forma.app.ui.components.KosherTag
import com.forma.app.ui.components.Note
import com.forma.app.ui.components.PrimaryButton
import com.forma.app.ui.components.SectionHeader
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.components.StatBlock
import com.forma.app.ui.components.Tag
import com.forma.app.ui.components.TextAction
import com.forma.app.ui.components.Tone
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Space
import com.forma.app.util.Fmt
import com.forma.engine.Portion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    val recipe = combine(c.recipes.recipe(id).filterNotNull(), c.recipes.ingredients(id)) { r, i -> RecipeFull(r, i) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayLog = c.energyModel.filterNotNull().map { it.s.today }.flatMapLatest { c.food.day(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val today: LocalDate get() = c.time.today()

    fun toggleFavorite() = viewModelScope.launch { recipe.value?.let { c.recipes.setFavorite(id, !it.recipe.isFavorite) } }
    fun setMilk(m: MilkOption) = viewModelScope.launch { c.recipes.setMilk(id, m) }

    fun log(meal: Category, portion: Portion, servings: Double, day: LocalDate, done: () -> Unit) = viewModelScope.launch {
        val at = if (day == c.time.today()) c.time.nowMillis() else c.time.startOfDayMillis(day) + 12 * 3_600_000L
        c.food.logRecipe(id, meal.name, portion, servings, day.toEpochDay(), at)
        done()
    }

    fun setPhoto(context: Context, uri: Uri?) = viewModelScope.launch {
        if (uri == null) { c.recipes.setPhoto(id, null); return@launch }
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "photos").apply { mkdirs() }
                val f = File(dir, "$id-${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                f.absolutePath
            }.getOrNull()
        }
        if (path != null) c.recipes.setPhoto(id, path)
    }
}

/** Loads a locally stored photo, downsampled for display. */
@Composable
fun rememberPhoto(path: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                var sample = 1
                while (o.outWidth / sample > 1400) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

@Composable
fun RecipeDetailScreen(nav: NavController, id: String) {
    val vm = formaViewModel(key = "recipe-$id") { RecipeDetailViewModel(it, id) }
    val full by vm.recipe.collectAsStateWithLifecycle()
    val todayLog by vm.todayLog.collectAsStateWithLifecycle()
    var portion by rememberSaveable { mutableStateOf(Portion.STANDARD) }
    var addSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setPhoto(context, uri) }
    val f = full ?: return Box(Modifier.fillMaxSize())
    val r = f.recipe
    val c = Forma.colors
    val macros = f.macros(portion)
    val milk = f.milk
    val kosher = KosherStatus.from(r.kosherStatus) ?: KosherStatus.PAREVE

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 96.dp).testTag("recipe_detail")) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.25f)) {
                RecipeImage(r, Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.clip(CircleShape).background(c.surface.copy(alpha = 0.92f))) {
                        IconAction(Icons.AutoMirrored.Outlined.ArrowBack, "Back", { nav.popBackStack() })
                    }
                    Row {
                        Box(Modifier.clip(CircleShape).background(c.surface.copy(alpha = 0.92f))) {
                            IconAction(Icons.Outlined.PhotoCamera, "Change photo", {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                        Spacer(Modifier.width(Space.s))
                        Box(Modifier.clip(CircleShape).background(c.surface.copy(alpha = 0.92f))) {
                            IconAction(if (r.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                if (r.isFavorite) "Remove from saved" else "Save recipe", { vm.toggleFavorite() },
                                tint = if (r.isFavorite) c.accent else c.text)
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = Space.gutter)) {
                Spacer(Modifier.height(Space.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    KosherTag(kosher, r.isFish)
                    Tag(Category.from(r.category).label)
                    Tag("${r.prepMinutes + r.cookMinutes} min")
                }
                Spacer(Modifier.height(Space.m))
                Text(r.name, style = Forma.type.title, color = c.text)
                Spacer(Modifier.height(Space.xs))
                Text(r.summary, style = Forma.type.body, color = c.textSecondary)
                if (r.photoUri != null) TextAction("Use illustration instead", { vm.setPhoto(context, null) }, color = c.textSecondary)

                Spacer(Modifier.height(Space.l))
                Segmented(Portion.entries, portion, { if (it == Portion.STANDARD) "Standard" else "Large" }, { portion = it },
                    Modifier.fillMaxWidth().testTag("portion_toggle"))
                Spacer(Modifier.height(Space.xs))
                Text(if (portion == Portion.STANDARD) r.standardPortionLabel else r.largePortionLabel,
                    style = Forma.type.caption, color = c.textTertiary)

                Spacer(Modifier.height(Space.m))
                FormaCard {
                    Row {
                        StatBlock("Calories", "${macros.kcal.roundToInt()}", "kcal", Modifier.weight(1.2f), style = Forma.type.numberLarge)
                        StatBlock("Protein", "${macros.proteinG.roundToInt()}", "g", Modifier.weight(1f), style = Forma.type.numberLarge)
                    }
                    Spacer(Modifier.height(Space.m))
                    Row {
                        StatBlock("Carbs", Fmt.grams(macros.carbsG), "g", Modifier.weight(1.2f))
                        StatBlock("Fat", Fmt.grams(macros.fatG), "g", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Space.m))
                    Text("Estimated from the ingredients below. Verify against your exact product labels and weighed portions.",
                        style = Forma.type.caption, color = c.textTertiary, modifier = Modifier.testTag("nutrition_kcal_${macros.kcal.roundToInt()}"))
                }
                if (r.referenceKcal != null && portion == Portion.STANDARD) {
                    val std = f.macros(Portion.STANDARD)
                    if (abs(std.kcal - r.referenceKcal) > 25 || abs(std.proteinG - (r.referenceProteinG ?: 0)) > 4) {
                        Spacer(Modifier.height(Space.s))
                        Note("Your starting estimate was ${r.referenceKcal} kcal · ${r.referenceProteinG} g protein. " +
                            "The ingredient-by-ingredient calculation gives ${std.kcal.roundToInt()} kcal · ${std.proteinG.roundToInt()} g, which is what FORMA logs.")
                    }
                }

                if (r.usesMilkChoice && milk != null) {
                    SectionHeader("Milk")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        MilkOption.entries.forEach { m -> Chip(m.label, m == milk, { vm.setMilk(m) }, Modifier.testTag("milk_${m.name}")) }
                    }
                    if (!milk.isDairy && kosher == KosherStatus.DAIRY) {
                        Spacer(Modifier.height(Space.s))
                        Note("Plant milk is pareve, but this recipe remains Dairy because of the whey protein or yogurt.")
                    }
                }

                SectionHeader("Ingredients")
                FormaCard {
                    f.ingredients.forEachIndexed { i, ing ->
                        if (i > 0) Hairline()
                        val amt = if (portion == Portion.LARGE) ing.largeAmount else ing.amount
                        val im = RecipeMath.ingredientMacros(ing, portion, milk)
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.Top) {
                            Text(
                                if (ing.unit == "to taste") "—" else "${Fmt.amount(amt)} ${ing.unit}",
                                style = Forma.type.numberSmall, color = c.text, modifier = Modifier.width(92.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(if (ing.isMilk && milk != null) milk.label else ing.name, style = Forma.type.body, color = c.text)
                                val sub = listOfNotNull(ing.note, if (ing.optional) "optional · not counted" else null, if (ing.unit == "to taste") "to taste" else null)
                                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = Forma.type.caption, color = c.textTertiary)
                            }
                            if (!ing.optional && im.kcal >= 1) Text("${im.kcal.roundToInt()}", style = Forma.type.numberSmall, color = c.textTertiary, modifier = Modifier.padding(start = Space.m))
                        }
                    }
                }

                SectionHeader("Preparation")
                Steps(r.prepSteps)
                SectionHeader("Cooking")
                Steps(r.cookSteps)

                SectionHeader("Kosher notes")
                FormaCard { r.kosherNotes.forEachIndexed { i, n -> if (i > 0) Spacer(Modifier.height(Space.s)); Bullet(n) } }
                if (r.safetyNotes.isNotEmpty()) {
                    SectionHeader("Food safety")
                    FormaCard { r.safetyNotes.forEachIndexed { i, n -> if (i > 0) Spacer(Modifier.height(Space.s)); Bullet(n) } }
                }
                if (r.tips.isNotEmpty()) {
                    SectionHeader("Notes")
                    r.tips.forEach { Text(it, style = Forma.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = Space.xs)) }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg).navigationBarsPadding()) {
            Column {
                Hairline()
                PrimaryButton("Add to today's log", { addSheet = true },
                    Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m).testTag("add_to_log"), icon = Icons.Outlined.Add)
            }
        }
    }

    if (addSheet) {
        AddRecipeSheet(f, portion, vm.today, todayLog, onDismiss = { addSheet = false }) { meal, p, servings, day ->
            vm.log(meal, p, servings, day) { addSheet = false }
        }
    }
}

@Composable
private fun Steps(steps: List<String>) {
    val c = Forma.colors
    Column {
        steps.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = Space.s)) {
                Text("${i + 1}", style = Forma.type.numberSmall, color = c.accent, modifier = Modifier.width(28.dp))
                Text(s, style = Forma.type.body, color = c.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    val c = Forma.colors
    Row {
        Box(Modifier.padding(top = 8.dp, end = Space.m).size(4.dp).clip(CircleShape).background(c.textTertiary))
        Text(text, style = Forma.type.bodySmall, color = c.textSecondary)
    }
}

/** Shared "add recipe to log" sheet: meal slot, portion, servings, day, plus a gentle meat/dairy check. */
@Composable
fun AddRecipeSheet(
    full: RecipeFull, initialPortion: Portion, today: LocalDate, todayLog: List<FoodLogEntity>,
    onDismiss: () -> Unit, onAdd: (Category, Portion, Double, LocalDate) -> Unit,
) {
    val c = Forma.colors
    var meal by remember { mutableStateOf(Category.from(full.recipe.category)) }
    var portion by remember { mutableStateOf(initialPortion) }
    var servings by remember { mutableStateOf(1.0) }
    var day by remember { mutableStateOf(today) }
    val m = full.macros(portion, servings)
    val kosher = KosherStatus.from(full.recipe.kosherStatus)
    val now = System.currentTimeMillis()
    val recentMeat = todayLog.any { it.kosherStatus == KosherStatus.MEAT.name && now - it.eatenAt < 6 * 3_600_000L }
    FormaSheet(onDismiss, full.recipe.name, "${m.kcal.roundToInt()} kcal · ${m.proteinG.roundToInt()} g protein") {
        Text("Meal", style = Forma.type.caption, color = c.textSecondary)
        Spacer(Modifier.height(Space.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Category.entries.forEach { Chip(it.label, it == meal, { meal = it }, Modifier.testTag("meal_${it.name}")) }
        }
        Spacer(Modifier.height(Space.l))
        Text("Portion", style = Forma.type.caption, color = c.textSecondary)
        Spacer(Modifier.height(Space.s))
        Segmented(Portion.entries, portion, { if (it == Portion.STANDARD) "Standard" else "Large" }, { portion = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.l))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Servings", style = Forma.type.caption, color = c.textSecondary)
                Text(Fmt.amount(servings).ifEmpty { "0" }, style = Forma.type.numberLarge, color = c.text)
            }
            IconAction(Icons.Outlined.Remove, "Fewer servings", { if (servings > 0.5) servings -= 0.5 })
            IconAction(Icons.Outlined.Add, "More servings", { if (servings < 6) servings += 0.5 })
        }
        Spacer(Modifier.height(Space.l))
        Text("Day", style = Forma.type.caption, color = c.textSecondary)
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Chip("Today", day == today, { day = today })
            Chip("Yesterday", day == today.minusDays(1), { day = today.minusDays(1) })
        }
        if (kosher == KosherStatus.DAIRY && recentMeat && day == today) {
            Spacer(Modifier.height(Space.l))
            Note("You logged a meat meal within the last few hours. Check your waiting time before having dairy.", Tone.WARN)
        }
        Spacer(Modifier.height(Space.xl))
        PrimaryButton("Add ${m.kcal.roundToInt()} kcal", { onAdd(meal, portion, servings, day) }, Modifier.fillMaxWidth().testTag("confirm_add"))
    }
}
