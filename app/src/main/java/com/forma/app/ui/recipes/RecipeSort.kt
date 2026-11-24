package com.forma.app.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
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
import com.forma.app.data.seed.KosherStatus
import com.forma.app.ui.components.FormaSheet
import com.forma.app.ui.components.Hairline
import com.forma.app.ui.components.Segmented
import com.forma.app.ui.theme.Forma
import com.forma.app.ui.theme.Radius
import com.forma.app.ui.theme.Space

/** How the recipe list is ordered. [descending] means "high to low" (or Meat first for kosher type). */
enum class RecipeSortKey(val label: String, val ascLabel: String, val descLabel: String) {
    RECOMMENDED("Recommended", "", ""),
    KOSHER("Kosher type", "Dairy → Pareve → Meat", "Meat → Pareve → Dairy"),
    PROTEIN("Protein", "Low to high", "High to low"),
    PREP_TIME("Prep time", "Shortest first", "Longest first"),
    TOTAL_TIME("Total time (prep + cook)", "Shortest first", "Longest first"),
    CALORIES("Calories", "Low to high", "High to low"),
    NAME("Name", "A → Z", "Z → A"),
}

data class RecipeSort(val key: RecipeSortKey = RecipeSortKey.RECOMMENDED, val descending: Boolean = false) {
    val label: String get() = when (key) {
        RecipeSortKey.RECOMMENDED -> "Recommended"
        else -> "${key.label.substringBefore(" (")} · ${if (descending) key.descLabel else key.ascLabel}"
    }

    fun encode() = "${key.name}:${if (descending) "desc" else "asc"}"

    companion object {
        fun decode(s: String?): RecipeSort {
            val (k, d) = (s ?: "").split(':').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "asc") }
            return RecipeSort(RecipeSortKey.entries.firstOrNull { it.name == k } ?: RecipeSortKey.RECOMMENDED, d == "desc")
        }

        /** Dairy first, then pareve (fish after plain pareve), then meat. "Depends on product" goes last. */
        fun kosherRank(r: RecipeCardData): Int = when (KosherStatus.from(r.recipe.kosherStatus)) {
            KosherStatus.DAIRY -> 0
            KosherStatus.PAREVE -> if (r.recipe.isFish) 2 else 1
            KosherStatus.MEAT -> 3
            KosherStatus.DEPENDS, null -> 4
        }
    }
}

/** Sorts recipes; ties fall back to the name so the order is stable and predictable. */
fun List<RecipeCardData>.sortedByRecipeSort(sort: RecipeSort): List<RecipeCardData> {
    val byName = compareBy<RecipeCardData> { it.recipe.name.lowercase() }
    val primary: Comparator<RecipeCardData> = when (sort.key) {
        RecipeSortKey.RECOMMENDED -> return sortedBy { it.recipe.sortOrder }
        RecipeSortKey.KOSHER -> compareBy { RecipeSort.kosherRank(it).let { r -> if (sort.descending && r < 4) 3 - r else r } }
        RecipeSortKey.PROTEIN -> compareBy { it.macros.proteinG }
        RecipeSortKey.PREP_TIME -> compareBy { it.recipe.prepMinutes }
        RecipeSortKey.TOTAL_TIME -> compareBy { it.recipe.prepMinutes + it.recipe.cookMinutes }
        RecipeSortKey.CALORIES -> compareBy { it.macros.kcal }
        RecipeSortKey.NAME -> return sortedWith(if (sort.descending) byName.reversed() else byName)
    }
    val ordered = if (sort.descending && sort.key != RecipeSortKey.KOSHER) primary.reversed() else primary
    return sortedWith(ordered.then(byName))
}

/** "Sort: Protein · High to low" row that opens the sort sheet. */
@Composable
fun SortRow(sort: RecipeSort, onChange: (RecipeSort) -> Unit, modifier: Modifier = Modifier) {
    val c = Forma.colors
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.clip(RoundedCornerShape(Radius.s)).clickable { open = true }.padding(vertical = Space.s).testTag("sort_button"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Sort, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(Space.xs))
        Text("Sort: ", style = Forma.type.label, color = c.textSecondary)
        Text(sort.label, style = Forma.type.label, color = c.accent, modifier = Modifier.testTag("sort_label"))
    }
    if (open) SortSheet(sort, { open = false }) { onChange(it); open = false }
}

@Composable
private fun SortSheet(current: RecipeSort, onDismiss: () -> Unit, onPick: (RecipeSort) -> Unit) {
    val c = Forma.colors
    var key by remember { mutableStateOf(current.key) }
    var desc by remember { mutableStateOf(current.descending) }
    FormaSheet(onDismiss, "Sort recipes") {
        RecipeSortKey.entries.forEachIndexed { i, k ->
            if (i > 0) Hairline()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).clickable {
                    key = k
                    // Sensible default direction for each choice
                    desc = when (k) { RecipeSortKey.PROTEIN, RecipeSortKey.PREP_TIME, RecipeSortKey.TOTAL_TIME -> true; else -> false }
                    if (k == RecipeSortKey.RECOMMENDED) onPick(RecipeSort())
                }.padding(vertical = Space.m).testTag("sort_${k.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(k.label, style = Forma.type.body, color = c.text, modifier = Modifier.weight(1f))
                if (k == key) Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
        if (key != RecipeSortKey.RECOMMENDED) {
            Spacer(Modifier.height(Space.m))
            Segmented(listOf(false, true), desc, { if (it) key.descLabel else key.ascLabel }, { desc = it }, Modifier.fillMaxWidth().testTag("sort_direction"))
            Spacer(Modifier.height(Space.l))
            com.forma.app.ui.components.PrimaryButton("Apply", { onPick(RecipeSort(key, desc)) }, Modifier.fillMaxWidth().testTag("sort_apply"))
        }
    }
}
