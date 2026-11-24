package com.forma.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forma.app.data.db.IngredientEntity
import com.forma.app.data.db.RecipeEntity
import com.forma.app.data.repo.TOTAL_UNIT
import com.forma.engine.Portion
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class CustomRecipeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val c get() = (rule.activity.application as FormaApp).container

    @Before fun ready() {
        runBlocking { c.initialize() }
        rule.waitUntil(8000) { rule.onAllNodesWithTagExists("remaining_kcal") }
    }

    private fun recipe(id: String) = RecipeEntity(
        id = id, name = "Turkey Chili", category = "SUPPER", kosherStatus = "MEAT", summary = "", prepMinutes = 10, cookMinutes = 30,
        prepSteps = listOf("Chop"), cookSteps = listOf("Simmer"), kosherNotes = emptyList(), safetyNotes = emptyList(), tips = emptyList(),
        standardPortionLabel = "Standard", largePortionLabel = "Large", artKey = "custom_SUPPER", builtIn = false,
    )

    @Test fun `custom recipe saves, scales per ingredient, edits and deletes`() = runBlocking {
        val ings = listOf(
            IngredientEntity("x", 0, "Ground turkey", 200.0, "g", 300.0, null, 300.0, 40.0, 0.0, 15.0),
            IngredientEntity("x", 1, "Olive oil", 1.0, "tsp", 1.0, null, 40.0, 0.0, 0.0, 4.5),
        )
        c.recipes.saveCustom(recipe("custom_1"), ings)
        val f = c.recipes.full("custom_1")!!
        assertFalse(f.recipe.builtIn)
        assertEquals(340.0, f.macros(Portion.STANDARD).kcal, 0.01)
        assertEquals(490.0, f.macros(Portion.LARGE).kcal, 0.01) // turkey ×1.5, oil unchanged
        c.recipes.saveCustom(recipe("custom_1").copy(name = "Turkey Chili v2"), ings.take(1))
        assertEquals("Turkey Chili v2", c.db.recipes().get("custom_1")!!.name)
        assertEquals(1, c.db.recipes().ingredients("custom_1").size)
        // Logged food keeps its own nutrition snapshot after the recipe is deleted
        c.food.logRecipe("custom_1", "SUPPER", Portion.STANDARD, 1.0, TestEnv.time.today().toEpochDay(), TestEnv.time.nowMillis())
        c.recipes.deleteCustom("custom_1")
        assertNull(c.db.recipes().get("custom_1"))
        assertEquals(300.0, c.db.food().all().single().kcal, 0.01)
    }

    @Test fun `total per serving mode and built-in protection`() = runBlocking {
        c.recipes.saveCustom(recipe("custom_2"), listOf(
            IngredientEntity("x", 0, "Lentils", 100.0, "g", 150.0, null, 0.0, 0.0, 0.0, 0.0),
            IngredientEntity("x", 1, "Nutrition per serving", 1.0, TOTAL_UNIT, 1.5, null, 420.0, 30.0, 50.0, 8.0),
        ))
        val f = c.recipes.full("custom_2")!!
        assertEquals(420.0, f.macros(Portion.STANDARD).kcal, 0.01)
        assertEquals(630.0, f.macros(Portion.LARGE).kcal, 0.01)
        val err = runCatching { c.recipes.saveCustom(recipe("salmon_poke_bowl"), emptyList()) }.exceptionOrNull()
        assertTrue(err?.message?.contains("Built-in") == true)
        assertEquals("Salmon Poke Bowl", c.db.recipes().get("salmon_poke_bowl")!!.name)
        c.recipes.seed() // reseeding never removes your recipes
        assertTrue(c.db.recipes().get("custom_2") != null)
        // Backup round trip keeps custom recipes
        val json = c.backup.encode(c.backup.snapshot(0))
        c.backup.resetAll(); assertNull(c.db.recipes().get("custom_2"))
        c.backup.restore(c.backup.decode(json))
        assertEquals(2, c.db.recipes().ingredients("custom_2").size)
    }

    @Test fun `create a recipe in the editor and log it`() {
        rule.onNodeWithTag("tab_recipes").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("new_recipe") }
        rule.onNodeWithTag("new_recipe").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("edit_name") }
        rule.onNodeWithTag("edit_name").performTextInput("Tuna Salad Wrap")
        rule.onNodeWithTag("edit_cat_LUNCH").performClick()
        rule.onNodeWithTag("ing_name_0").performTextInput("Canned tuna")
        rule.onNodeWithTag("ing_amount_0").performTextInput("120")
        rule.onNodeWithTag("ing_kcal_0").performTextInput("140")
        rule.onNodeWithTag("ing_protein_0").performTextInput("30")
        rule.onNodeWithTag("add_ingredient").performScrollTo().performClick()
        rule.onNodeWithTag("ing_name_1").performScrollTo().performTextInput("Whole wheat wrap")
        rule.onNodeWithTag("ing_amount_1").performTextInput("1")
        rule.onNodeWithTag("ing_kcal_1").performTextInput("130")
        rule.onNodeWithTag("ing_protein_1").performTextInput("4")
        rule.onNodeWithTag("edit_prep").performScrollTo().performTextInput("Drain the tuna\nMix with mustard")
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/10_recipe_editor_light.png")
        rule.onNodeWithTag("save_recipe").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_detail") }
        val saved = runBlocking { c.db.recipes().all() }.single { !it.builtIn }
        assertEquals("Tuna Salad Wrap", saved.name); assertEquals("LUNCH", saved.category)
        assertEquals(listOf("Drain the tuna", "Mix with mustard"), saved.prepSteps)
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("nutrition_kcal_270") }
        rule.onRoot().captureRoboImage("screenshots/11_custom_recipe_detail_light.png")
        rule.onNodeWithTag("add_to_log").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("confirm_add") }
        rule.onNodeWithTag("confirm_add").performClick()
        rule.waitUntil(5000) { runBlocking { c.db.food().all() }.size == 1 }
        assertEquals(270.0, runBlocking { c.db.food().all() }.single().kcal, 0.01)
    }

    @Test fun `copy a built-in recipe without changing the original`() {
        rule.onNodeWithTag("tab_recipes").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("recipe_protein_egg_scramble") }
        rule.onNodeWithTag("recipe_protein_egg_scramble").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("copy_recipe") }
        rule.onNodeWithTag("copy_recipe").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTagExists("edit_name") && rule.onAllNodesWithTagExists("ing_name_0") }
        rule.onNodeWithTag("save_recipe").performScrollTo().performClick()
        rule.waitUntil(5000) { runBlocking { c.db.recipes().all() }.any { !it.builtIn } }
        val copy = runBlocking { c.db.recipes().all() }.single { !it.builtIn }
        assertEquals("Protein Egg Scramble (my version)", copy.name)
        val orig = runBlocking { c.recipes.full("protein_egg_scramble")!! }
        val mine = runBlocking { c.recipes.full(copy.id)!! }
        assertEquals(orig.macros(Portion.STANDARD).kcal, mine.macros(Portion.STANDARD).kcal, 1.0)
        assertEquals(orig.ingredients.size, mine.ingredients.size)
    }
}
