package com.forma.app.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.forma.app.data.db.RecipeEntity
import com.forma.app.data.repo.RecipeMath
import com.forma.app.data.seed.Category
import com.forma.app.data.seed.KosherStatus
import com.forma.app.data.seed.MilkOption
import com.forma.app.di.AppContainer
import com.forma.app.ui.components.FoodArt
import com.forma.app.ui.components.FormaField
import com.forma.app.ui.components.KosherTag
import com.forma.app.ui.components.ScreenTitle
import com.forma.app.ui.components.Chip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.forma.app.ui.formaViewModel
import com.forma.app.ui.nav.Routes
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Radius
import com.forma.app.ui.theme.Space
import com.forma.engine.Macros
import com.forma.engine.Portion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeCardData(val recipe: RecipeEntity, val macros: Macros)

class RecipesViewModel(private val c: AppContainer) : ViewModel() {
    val recipes = combine(c.recipes.recipes, c.recipes.allIngredients) { rs, ings ->
        val byId = ings.groupBy { it.recipeId }
        rs.map { r ->
            val milk = if (r.usesMilkChoice) MilkOption.from(r.selectedMilk) ?: MilkOption.from(r.defaultMilk) else null
            RecipeCardData(r, RecipeMath.macros(byId[r.id].orEmpty(), Portion.STANDARD, 1.0, milk))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(r: RecipeEntity) = viewModelScope.launch { c.recipes.setFavorite(r.id, !r.isFavorite) }
}

/** Library filter: the four collections plus favourites. */
private enum class Filter(val label: String, val cat: Category?) {
    BREAKFAST("Breakfast", Category.BREAKFAST), LUNCH("Lunch", Category.LUNCH), SUPPER("Supper", Category.SUPPER),
    SNACKS("Snacks", Category.SNACKS), FAVORITES("Saved", null), MINE("My recipes", null)
}

@Composable
fun RecipesScreen(nav: NavController) {
    val vm = formaViewModel { RecipesViewModel(it) }
    val all by vm.recipes.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.BREAKFAST) }
    var query by rememberSaveable { mutableStateOf("") }
    val c = Forma.colors
    val shown = all.filter {
        (when (filter) {
            Filter.FAVORITES -> it.recipe.isFavorite
            Filter.MINE -> !it.recipe.builtIn
            else -> it.recipe.category == filter.cat!!.name
        }) &&
            (query.isBlank() || it.recipe.name.contains(query, true) || it.recipe.summary.contains(query, true) || it.recipe.tags.any { t -> t.contains(query.trim().replace(' ', '-'), true) })
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().testTag("recipes"),
        contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            ScreenTitle("Recipes", subtitle = "${all.size} kosher recipes") {
                com.forma.app.ui.components.TextAction("Import", { nav.navigate(Routes.IMPORT) }, color = Forma.colors.textSecondary, modifier = Modifier.testTag("import_recipes"))
                com.forma.app.ui.components.TextAction("+ New", { nav.navigate(Routes.newRecipe()) }, modifier = Modifier.testTag("new_recipe"))
            }
            Spacer(Modifier.height(Space.m))
            FormaField(query, { query = it }, "Search recipes")
            Spacer(Modifier.height(Space.m))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Filter.entries.forEach { f -> Chip(f.label, f == filter, { filter = f }, Modifier.testTag("filter_${f.name}")) }
            }
        }
        if (shown.isEmpty()) item {
            Text(when {
                filter == Filter.FAVORITES -> "Tap the heart on any recipe to save it here."
                filter == Filter.MINE && query.isBlank() -> "Recipes you create appear here. Tap + New, or open any recipe and choose Copy & edit."
                else -> "No recipes match your search."
            },
                style = Forma.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(vertical = Space.xl))
        }
        items(shown, key = { it.recipe.id }) { d ->
            RecipeRow(d, onClick = { nav.navigate(Routes.recipe(d.recipe.id)) }, onFavorite = { vm.toggleFavorite(d.recipe) })
        }
    }
}

@Composable
fun RecipeRow(d: RecipeCardData, onClick: () -> Unit, onFavorite: (() -> Unit)? = null) {
    val c = Forma.colors
    val shape = RoundedCornerShape(Radius.l)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.line, shape).clickable(onClick = onClick)
            .padding(Space.m).testTag("recipe_${d.recipe.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(d.recipe, Modifier.size(84.dp).clip(RoundedCornerShape(Radius.m)))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(d.recipe.name, style = Forma.type.heading, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                "${d.macros.kcal.toInt()} kcal · ${d.macros.proteinG.toInt()} g protein",
                style = Forma.type.bodySmall, color = c.textSecondary,
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                KosherTag(KosherStatus.from(d.recipe.kosherStatus) ?: KosherStatus.PAREVE, d.recipe.isFish)
                val mins = d.recipe.prepMinutes + d.recipe.cookMinutes
                Text("$mins min", style = Forma.type.caption, color = c.textTertiary)
            }
        }
        if (onFavorite != null) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickable(onClick = onFavorite), contentAlignment = Alignment.Center) {
                Icon(if (d.recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (d.recipe.isFavorite) "Remove from saved" else "Save recipe",
                    tint = if (d.recipe.isFavorite) c.accent else c.textTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** User photo when set, otherwise the generated illustration. */
@Composable
fun RecipeImage(r: RecipeEntity, modifier: Modifier) {
    val bmp = rememberPhoto(r.photoUri)
    if (bmp != null) {
        androidx.compose.foundation.Image(bmp, r.name, modifier, contentScale = ContentScale.Crop)
    } else {
        FoodArt(r.artKey, modifier, description = "${r.name} illustration")
    }
}
