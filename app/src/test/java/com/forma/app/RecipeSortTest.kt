package com.forma.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.RecipeEntity
import com.forma.app.ui.recipes.RecipeCardData
import com.forma.app.ui.recipes.RecipeSort
import com.forma.app.ui.recipes.RecipeSortKey
import com.forma.app.ui.recipes.sortedByRecipeSort
import com.forma.engine.Macros
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestFormaApp::class, sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class RecipeSortTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container

    private fun r(name: String, kosher: String, protein: Double, prep: Int, cook: Int = 0, fish: Boolean = false, kcal: Double = 400.0, order: Int = 0) =
        RecipeCardData(RecipeEntity(name, name, "SUPPER", kosher, fish, "", prep, cook, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            "", "", artKey = "x", sortOrder = order), Macros(kcal, protein))

    private val sample = listOf(
        r("Beef", "MEAT", 46.0, 15, 25, order = 0), r("Yogurt", "DAIRY", 20.0, 3, order = 1), r("Salmon", "PAREVE", 45.0, 10, 30, fish = true, order = 2),
        r("Eggs", "PAREVE", 31.0, 5, 8, order = 3), r("Chicken", "MEAT", 55.0, 10, 35, order = 4), r("Shake", "DEPENDS", 25.0, 2, order = 5),
    )
    private fun names(s: RecipeSort) = sample.sortedByRecipeSort(s).map { it.recipe.name }

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    @Test fun `sort orders`() {
        assertEquals(listOf("Yogurt", "Eggs", "Salmon", "Beef", "Chicken", "Shake"), names(RecipeSort(RecipeSortKey.KOSHER)))
        assertEquals(listOf("Beef", "Chicken", "Salmon", "Eggs", "Yogurt", "Shake"), names(RecipeSort(RecipeSortKey.KOSHER, descending = true)))
        assertEquals(listOf("Chicken", "Beef", "Salmon", "Eggs", "Shake", "Yogurt"), names(RecipeSort(RecipeSortKey.PROTEIN, descending = true)))
        assertEquals(listOf("Yogurt", "Shake", "Eggs", "Salmon", "Beef", "Chicken"), names(RecipeSort(RecipeSortKey.PROTEIN)))
        assertEquals(listOf("Beef", "Chicken", "Salmon", "Eggs", "Yogurt", "Shake"), names(RecipeSort(RecipeSortKey.PREP_TIME, descending = true))) // ties by name
        assertEquals(listOf("Chicken", "Beef", "Salmon", "Eggs", "Yogurt", "Shake"), names(RecipeSort(RecipeSortKey.TOTAL_TIME, descending = true))) // Beef and Salmon tie at 40 min → by name
        assertEquals(listOf("Beef", "Chicken", "Eggs", "Salmon", "Shake", "Yogurt"), names(RecipeSort(RecipeSortKey.NAME)))
        assertEquals(sample.map { it.recipe.name }, names(RecipeSort()))
        assertEquals(RecipeSort(RecipeSortKey.PROTEIN, true), RecipeSort.decode(RecipeSort(RecipeSortKey.PROTEIN, true).encode()))
    }

    @Test fun `sort supper in the app and remember it`() {
        rule.onNodeWithTag("tab_recipes").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("filter_SUPPER") }
        rule.onNodeWithTag("filter_SUPPER").performClick()

        fun sortBy(key: String, descending: Boolean) {
            rule.onNodeWithTag("sort_button").performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithTagExists("sort_$key") }
            rule.onNodeWithTag("sort_$key").performClick()
            rule.onNode(androidx.compose.ui.test.hasText(if (descending) when (key) { "KOSHER" -> "Meat → Pareve → Dairy"; else -> if (key.contains("TIME")) "Longest first" else "High to low" }
                else when (key) { "KOSHER" -> "Dairy → Pareve → Meat"; else -> if (key.contains("TIME")) "Shortest first" else "Low to high" })).performClick()
            rule.onNodeWithTag("sort_apply").performClick()
            rule.waitForIdle()
        }
        fun firstRecipe(): String = rule.onAllNodes(hasTestTagPrefix("recipe_")).fetchSemanticsNodes()
            .first().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("recipe_")

        sortBy("KOSHER", false)
        rule.waitUntil(5000) { rule.onNodeWithTag("sort_label", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }.startsWith("Kosher") }
        rule.waitUntil(5000) { firstRecipe() == "lemon_garlic_salmon" } // supper has no dairy; pareve (fish) before meat
        rule.onRoot().captureRoboImage("screenshots/26_recipes_sorted_kosher_light.png")
        sortBy("PROTEIN", true)
        val supper = runBlocking { c.db.recipes().all().filter { it.category == "SUPPER" }.associate { it.id to c.recipes.full(it.id)!!.macros(com.forma.engine.Portion.STANDARD).proteinG } }
        rule.waitUntil(5000) { firstRecipe() == supper.maxBy { it.value }.key }
        sortBy("PREP_TIME", true)
        rule.waitUntil(5000) { firstRecipe() in runBlocking { c.db.recipes().all() }.filter { it.category == "SUPPER" }.let { l -> l.filter { it.prepMinutes == l.maxOf { x -> x.prepMinutes } }.map { it.id } } }
        assertTrue(runBlocking { c.settings.recipeSort.first() } == "PREP_TIME:desc")
        // Remembered after leaving and coming back
        rule.onNodeWithTag("tab_home").performClick()
        rule.onNodeWithTag("tab_recipes").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("sort_label") }
        assertTrue(rule.onNodeWithTag("sort_label", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }.startsWith("Prep time"))
    }
}

